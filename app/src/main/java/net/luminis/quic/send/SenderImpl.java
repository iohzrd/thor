/*
 * Copyright © 2020 Peter Doornbosch
 *
 * This file is part of Kwik, a QUIC client Java library
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.quic.send;

import net.luminis.quic.*;
import net.luminis.quic.cc.CongestionControlEventListener;
import net.luminis.quic.cc.CongestionController;
import net.luminis.quic.cc.NewRenoCongestionController;
import net.luminis.quic.crypto.ConnectionSecrets;
import net.luminis.quic.crypto.Keys;
import net.luminis.quic.frame.QuicFrame;
import net.luminis.quic.log.Logger;
import net.luminis.quic.recovery.RecoveryManager;
import net.luminis.quic.recovery.RttEstimator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Long.max;

/**
 * Sender implementation that queues frames-to-be-sent and assembles packets "just in time" when conditions allow to
 * send a packet.
 *
 * Sending packets is limited by congestion controller, anti-amplification attack limitations and, for stream frames,
 * flow control. However, ack-only packets are not subject to congestion control and probes are not limited by
 * congestion control (but do count); therefore, such packets have priority when other packets are queued because of
 * congestion control limits. Additionally, delayed ack packets don't have to be send immediately, but they have to
 * within a given time frame.
 * To improve packet and frame coalescing, messages should not be sent immediately when there is the expectation that
 * more will follow in due time.
 *
 * So a sender has to wait for any of the following conditions to become true:
 * - received packet completely processed or batch of packets processed and send requests queued
 * - "spontaneous" request queued (e.g. application initiated stream data)
 * - probe request
 * - delayed ack timeout
 * - congestion controller becoming unblocked due to timer-induced loss detection
 */
public class SenderImpl implements Sender, CongestionControlEventListener {

    private final int maxPacketSize;
    private volatile DatagramSocket socket;
    private final InetSocketAddress peerAddress;
    private final QuicConnectionImpl connection;
    private final CongestionController congestionController;
    private final RttEstimator rttEstimater;
    private final Logger log;
    private final SendRequestQueue[] sendRequestQueue = new SendRequestQueue[EncryptionLevel.values().length];
    private final GlobalPacketAssembler packetAssembler;
    private final GlobalAckGenerator globalAckGenerator;
    private final RecoveryManager recoveryManager;
    private final IdleTimer idleTimer;
    private final Thread senderThread;
    private final boolean[] discardedSpaces = new boolean[PnSpace.values().length];
    private ConnectionSecrets connectionSecrets;
    private final Object condition = new Object();
    private boolean signalled;

    private volatile boolean running;
    private volatile int receiverMaxAckDelay;
    private volatile int datagramsSent;
    private volatile long bytesSent;
    private volatile long packetsSent;


    public SenderImpl(Version version, int maxPacketSize, DatagramSocket socket, InetSocketAddress peerAddress,
                      QuicConnectionImpl connection, Integer initialRtt, Logger log) {
        this.maxPacketSize = maxPacketSize;
        this.socket = socket;
        this.peerAddress = peerAddress;
        this.connection = connection;
        this.log = log;

        Arrays.stream(EncryptionLevel.values()).forEach(level -> {
            int levelIndex = level.ordinal();
            sendRequestQueue[levelIndex] = new SendRequestQueue();
        });
        globalAckGenerator = new GlobalAckGenerator(this);
        packetAssembler = new GlobalPacketAssembler(version, sendRequestQueue, globalAckGenerator, connection.getMaxPacketSize());

        congestionController = new NewRenoCongestionController(log, this);
        rttEstimater = (initialRtt == null)? new RttEstimator(log): new RttEstimator(log, initialRtt);

        recoveryManager = new RecoveryManager(connection, rttEstimater, congestionController, this, log);
        connection.addHandshakeStateListener(recoveryManager);

        idleTimer = connection.getIdleTimer();

        senderThread = new Thread(() -> sendLoop(), "sender-loop");
        senderThread.setDaemon(true);
    }

    public void start(ConnectionSecrets secrets) {
        connectionSecrets = secrets;
        senderThread.start();
    }

    @Override
    public void send(QuicFrame frame, EncryptionLevel level) {
        sendRequestQueue[level.ordinal()].addRequest(frame, f -> {});
    }

    @Override
    public void send(QuicFrame frame, EncryptionLevel level, Consumer<QuicFrame> frameLostCallback) {
        sendRequestQueue[level.ordinal()].addRequest(frame, frameLostCallback);
    }

    @Override
    public void send(Function<Integer, QuicFrame> frameSupplier, int minimumSize, EncryptionLevel level, Consumer<QuicFrame> lostCallback) {
        sendRequestQueue[level.ordinal()].addRequest(frameSupplier, minimumSize, lostCallback);
    }

    @Override
    public void setInitialToken(byte[] token) {
        if (token != null) {
            packetAssembler.setInitialToken(token);
        }
    }

    @Override
    public void sendAck(PnSpace pnSpace, int maxDelay) {
        sendRequestQueue[pnSpace.relatedEncryptionLevel().ordinal()].addAckRequest(maxDelay);
        if (maxDelay > 0) {
            // Now, the sender loop must use a different wait-period, to ensure it wakes up when the delayed ack
            // must be sent.
            // However, given the current implementation of packetProcessed (i.e. it always wakes up the sender loop),
            // it is not necessary to do this with a ...
            // senderThread.interrupt
            // ... because packetProcessed will ensure the new period is computed.
        }
    }

    @Override
    public void sendProbe(EncryptionLevel level) {
        synchronized (discardedSpaces) {
            if (! discardedSpaces[level.relatedPnSpace().ordinal()]) {
                sendRequestQueue[level.ordinal()].addProbeRequest();
                wakeUpSenderLoop();
            }
            else {
                log.warn("Attempt to send probe on discarded space (" + level.relatedPnSpace() + ") => moving to next");
                level.next().ifPresent(nextLevel -> sendProbe(nextLevel));
            }
        }
    }

    @Override
    public void sendProbe(List<QuicFrame> frames, EncryptionLevel level) {
        synchronized (discardedSpaces) {
            if (! discardedSpaces[level.relatedPnSpace().ordinal()]) {
                sendRequestQueue[level.ordinal()].addProbeRequest(frames);
                wakeUpSenderLoop();
            }
            else {
                log.warn("Attempt to send probe on discarded space (" + level.relatedPnSpace() + ") => moving to next");
                // Do not move frames from one level to another, just create a ping probe.
                level.next().ifPresent(nextLevel -> sendProbe(nextLevel));
            }
        }
    }

    @Override
    public void packetProcessed(boolean expectingMore) {
        wakeUpSenderLoop();  // If you change this, review sendAck!
    }

    @Override
    public void datagramProcessed(boolean expectingMore) {
        // Nothing, current implementation flushes when packet processed
    }

    @Override
    public void flush() {
        wakeUpSenderLoop();
    }
    
    public void changeAddress(DatagramSocket newSocket) {
        socket = newSocket;
    }

    public void discard(PnSpace space, String reason) {
        synchronized (discardedSpaces) {
            if (!discardedSpaces[space.ordinal()]) {
                log.recovery("Discarding pn space " + space + " because " + reason);
                packetAssembler.stop(space);
                recoveryManager.stopRecovery(space);
                if (sendRequestQueue[space.relatedEncryptionLevel().ordinal()].hasProbe()) {
                    log.warn("Discarding space " + space + " that has a probe queued.");
                }
                sendRequestQueue[space.ordinal()].clear();
                globalAckGenerator.discard(space);
                discardedSpaces[space.ordinal()] = true;
            }
        }
    }

    /**
     * Stop sending packets, but don't shutdown yet, so connection close can be sent.
     */
    public void stop() {
        // Stop sending packets, so discard any packet waiting to be send.
        Arrays.stream(sendRequestQueue).forEach(sendRequestQueue -> sendRequestQueue.clear());

        // No more retransmissions either.
        recoveryManager.stopRecovery();
    }

    public void shutdown() {
        running = false;
        senderThread.interrupt();
    }

    @Override
    public void bytesInFlightIncreased(long bytesInFlight) {
    }

    @Override
    public void bytesInFlightDecreased(long bytesInFlight) {
        wakeUpSenderLoop();
    }

    private void sendLoop() {
        try {
            running = true;
            while (running) {
                boolean interrupted = false;
                synchronized (condition) {
                    try {
                        if (! signalled) {
                            long timeout = determineMinimalDelay();
                            if (timeout > 0) {
                                condition.wait(timeout);
                            }
                        }
                        signalled = false;
                    }
                    catch (InterruptedException e) {
                        interrupted = true;
                        log.debug("Sender thread is interrupted; shutting down? " + running);
                    }
                }
                if (! interrupted) {
                    sendIfAny();
                }
            }
        }
        catch (Throwable fatalError) {
            if (running) {
                log.error("Sender thread aborted with exception", fatalError);
                connection.abortConnection(fatalError);
            }
            else {
                log.warn("Ignoring " + fatalError + " because sender is shutting down.");
            }
        }
    }

    void sendIfAny() throws IOException {
        List<SendItem> items;
        do {
            items = assemblePacket();
            if (!items.isEmpty()) {
                send(items);
            }
        }
        while (!items.isEmpty());
    }

    private void wakeUpSenderLoop() {
        synchronized (condition) {
            signalled = true;
            condition.notify();
        }
    }

    private long determineMinimalDelay() {
        Optional<Instant> nextDelayedSendTime = Arrays.stream(sendRequestQueue)
                .map(q -> q.nextDelayedSend())
                .filter(Objects::nonNull)     // Filter after mapping because value can become null during iteration
                .findFirst();

        if (nextDelayedSendTime.isPresent()) {
            long delay = max(Duration.between(Instant.now(), nextDelayedSendTime.get()).toMillis(), 0);
            if (delay > 0) {
                return delay;
            }
            else {
                // Next time is already in the past, hurry up!
                return 0;
            }
        }

        // No timeout needed, just wait for next action. In theory, infinity should be returned.
        // However, in order to somewhat forgiving for bugs that would lead to deadlocking the sender, use a
        // value that will keep the connection going, but also indicates there is something wrong.
        return 5000;
    }

    void send(List<SendItem> itemsToSend) throws IOException {
        byte[] datagramData = new byte[maxPacketSize];
        ByteBuffer buffer = ByteBuffer.wrap(datagramData);
        itemsToSend.stream()
                .map(item -> item.getPacket())
                .forEach(packet -> {
                    Keys keys = connectionSecrets.getOwnSecrets(packet.getEncryptionLevel());
                    byte[] packetData = packet.generatePacketBytes(packet.getPacketNumber(), keys);
                    buffer.put(packetData);
                    log.raw("packet sent, pn: " + packet.getPacketNumber(), packetData);
                });

        DatagramPacket datagram = new DatagramPacket(datagramData, buffer.position(), peerAddress.getAddress(), peerAddress.getPort());

        Instant timeSent = Instant.now();
        socket.send(datagram);
        datagramsSent++;
        packetsSent += itemsToSend.size();
        bytesSent += buffer.position();

        itemsToSend.stream()
                .forEach(item -> {
                    recoveryManager.packetSent(item.getPacket(), timeSent, item.getPacketLostCallback());
                    idleTimer.packetSent(item.getPacket(), timeSent);
                });

        itemsToSend.stream()
                .map(item -> item.getPacket())
                .forEach(packett -> log.sent(timeSent, packett));
    }

    private List<SendItem> assemblePacket() {
        int remainingCwnd = (int) congestionController.remainingCwnd();
        byte[] srcCid = connection.getSourceConnectionId();
        byte[] destCid = connection.getDestinationConnectionId();
        return packetAssembler.assemble(remainingCwnd, srcCid, destCid);
    }

    private Instant earliest(Instant instant1, Instant instant2) {
        if (instant1 == null) {
            return instant2;
        }
        if (instant2 == null) {
            return instant1;
        }
        if (instant1.isBefore(instant2)) {
            return instant1;
        }
        else {
            return instant2;
        }
    }

    public SendStatistics getStatistics() {
        return new SendStatistics(datagramsSent, packetsSent, bytesSent, recoveryManager.getLost());
    }

    public int getPto() {
        return rttEstimater.getSmoothedRtt() + 4 * rttEstimater.getRttVar() + receiverMaxAckDelay;
    }

    public CongestionController getCongestionController() {
        return congestionController;
    }

    public void setReceiverMaxAckDelay(int maxAckDelay) {
        this.receiverMaxAckDelay = maxAckDelay;
        rttEstimater.setMaxAckDelay(maxAckDelay);
    }

    public GlobalAckGenerator getGlobalAckGenerator() {
        return globalAckGenerator;
    }
}

