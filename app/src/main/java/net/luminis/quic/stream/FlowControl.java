/*
 * Copyright © 2019, 2020 Peter Doornbosch
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
package net.luminis.quic.stream;

import net.luminis.quic.*;
import net.luminis.quic.frame.MaxDataFrame;
import net.luminis.quic.frame.MaxStreamDataFrame;
import net.luminis.quic.frame.QuicFrame;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.NullLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of connection and stream flow control limits.
 */
public class FlowControl {

    // https://tools.ietf.org/html/draft-ietf-quic-transport-23#section-18.2

    // "The initial maximum data parameter is an integer value that contains the initial value for the maximum
    //  amount of data that can be sent on the connection."
    private final long initialMaxData;

    // "initial_max_stream_data_bidi_local (0x0005):  This parameter is an integer value specifying the initial flow control limit for
    //  locally-initiated bidirectional streams.
    private final long initialMaxStreamDataBidiLocal;

    // "initial_max_stream_data_bidi_remote (0x0006):  This parameter is an integer value specifying the initial flow control limit for peer-
    //  initiated bidirectional streams. "
    private final long initialMaxStreamDataBidiRemote;

    // "initial_max_stream_data_uni (0x0007):  This parameter is an integer value specifying the initial flow control limit for unidirectional
    //  streams."
    private final long initialMaxStreamDataUni;

    private long maxDataAllowed;
    private long maxDataAssigned;
    private Map<Integer, Long> maxStreamDataAllowed;
    private Map<Integer, Long> maxStreamDataAssigned;
    private final Logger log;


    public FlowControl(long initialMaxData, long initialMaxStreamDataBidiLocal, long initialMaxStreamDataBidiRemote, long initialMaxStreamDataUni) {
        this(initialMaxData, initialMaxStreamDataBidiLocal, initialMaxStreamDataBidiRemote, initialMaxStreamDataUni, new NullLogger());
    }

    public FlowControl(long initialMaxData, long initialMaxStreamDataBidiLocal, long initialMaxStreamDataBidiRemote, long initialMaxStreamDataUni, Logger log) {
        this.initialMaxData = initialMaxData;
        this.initialMaxStreamDataBidiLocal = initialMaxStreamDataBidiLocal;
        this.initialMaxStreamDataBidiRemote = initialMaxStreamDataBidiRemote;
        this.initialMaxStreamDataUni = initialMaxStreamDataUni;
        this.log = log;

        maxDataAllowed = initialMaxData;
        maxDataAssigned = 0;
        maxStreamDataAllowed = new HashMap<>();
        maxStreamDataAssigned = new HashMap<>();
    }

    /**
     * Request to increase the flow control limit for the indicated stream to the indicated value. Whether this is
     * possible depends on whether the stream flow control limit allows this and whether the connection flow control
     * limit has enough "unused" credits.
     * @param stream
     * @param requestedLimit
     * @return the new flow control limit for the stream: the offset of the last byte sent on the stream may not past this limit.
     */
    public long increaseFlowControlLimit(QuicStream stream, long requestedLimit) {
        int streamId = stream.getStreamId();
        synchronized (this) {
            long possibleStreamIncrement = currentStreamCredits(stream);
            long requestedIncrement = requestedLimit - maxStreamDataAssigned.get(streamId);
            long proposedStreamIncrement = Long.min(requestedIncrement, possibleStreamIncrement);

            if (requestedIncrement < 0) {
                throw new IllegalArgumentException();
            }

            maxDataAssigned += proposedStreamIncrement;
            long newStreamLimit = maxStreamDataAssigned.get(streamId) + proposedStreamIncrement;
            maxStreamDataAssigned.put(streamId, newStreamLimit);

            return newStreamLimit;
        }
    }

    public long getFlowControlLimit(QuicStream stream) {
        synchronized (this) {
            return currentStreamCredits(stream);
        }
    }

    /**
     * Waits for flow control credits. Returns immediately when credits are available for the given
     * stream, blocks until credits become available otherwise.
     * @param stream
     * @throws InterruptedException
     */
    public void waitForFlowControlCredits(QuicStream stream) throws InterruptedException {
        if (log.logFlowControl()) {
            // This piece of code can be part of a race condition, but for logging this is less problematic; logging from a synchronized block is worse.
            if (currentStreamCredits(stream) == 0) {
                log.fc("Flow control: stream " + stream.getStreamId() + " blocked");
                // Note that with the current (Sender) implementation, blocking might be caused by congestion control all well.
                // That's why no (stream) data blocked frame should be sent at this point.
            }
        }

        boolean wasBlocked = false;
        synchronized (this) {
            while (true) {
                if (currentStreamCredits(stream) > 0) {
                    break;
                }
                wasBlocked = true;
                this.wait();
            }
        }

        if (wasBlocked && log.logFlowControl()) {
            log.fc("Flow control: stream " + stream.getStreamId() + " not blocked anymore");
        }
    }

    public synchronized void updateInitialValues(TransportParameters transportParameters) {
        if (transportParameters.getInitialMaxData() > initialMaxData) {
            log.info("Increasing initial max data from " + initialMaxData + " to " + transportParameters.getInitialMaxData());
            if (transportParameters.getInitialMaxData() > maxDataAllowed) {
                maxDataAllowed = transportParameters.getInitialMaxData();
            }
        }
        else if (transportParameters.getInitialMaxData() < initialMaxData) {
            log.error("Ignoring attempt to reduce initial max data from " + initialMaxData + " to " + transportParameters.getInitialMaxData());
        }

        if (transportParameters.getInitialMaxStreamDataBidiLocal() > initialMaxStreamDataBidiLocal) {
            log.info("Increasing initial max data from " + initialMaxStreamDataBidiLocal + " to " + transportParameters.getInitialMaxStreamDataBidiLocal());
            maxStreamDataAllowed.entrySet().stream()
                    // Find all server initiated bidirectional streams
                    .filter(entry -> entry.getKey() % 4 == 1)
                    .forEach(entry -> {
                        if (transportParameters.getInitialMaxStreamDataBidiLocal() > entry.getValue()) {
                            maxStreamDataAllowed.put(entry.getKey(), transportParameters.getInitialMaxStreamDataBidiLocal());
                        }
                    });
        }
        else if (transportParameters.getInitialMaxStreamDataBidiLocal() < initialMaxStreamDataBidiLocal) {
            log.error("Ignoring attempt to reduce max data from " + initialMaxStreamDataBidiLocal + " to " + transportParameters.getInitialMaxStreamDataBidiLocal());
        }

        if (transportParameters.getInitialMaxStreamDataBidiRemote() > initialMaxStreamDataBidiRemote) {
            log.info("Increasing initial max data from " + initialMaxStreamDataBidiRemote + " to " + transportParameters.getInitialMaxStreamDataBidiRemote());
            maxStreamDataAllowed.entrySet().stream()
                    // Find all client initiated bidirectional streams
                    .filter(entry -> entry.getKey() % 4 == 0)
                    .forEach(entry -> {
                        if (transportParameters.getInitialMaxStreamDataBidiRemote() > entry.getValue()) {
                            maxStreamDataAllowed.put(entry.getKey(), transportParameters.getInitialMaxStreamDataBidiRemote());
                        }
                    });
        }
        else if (transportParameters.getInitialMaxStreamDataBidiRemote() < initialMaxStreamDataBidiRemote) {
            log.error("Ignoring attempt to reduce max data from " + initialMaxStreamDataBidiRemote + " to " + transportParameters.getInitialMaxStreamDataBidiRemote());
        }

        if (transportParameters.getInitialMaxStreamDataUni() > initialMaxStreamDataUni) {
            log.info("Increasing initial max data from " + initialMaxStreamDataUni + " to " + transportParameters.getInitialMaxStreamDataUni());
            maxStreamDataAllowed.entrySet().stream()
                    // Find all client initiated unidirectional streams
                    .filter(entry -> entry.getKey() % 4 == 2)
                    .forEach(entry -> {
                        if (transportParameters.getInitialMaxStreamDataUni() > entry.getValue()) {
                            maxStreamDataAllowed.put(entry.getKey(), transportParameters.getInitialMaxStreamDataUni());
                        }
                    });
        }
        else if (transportParameters.getInitialMaxStreamDataUni() < initialMaxStreamDataUni) {
            log.error("Ignoring attempt tomax data from " + initialMaxStreamDataUni + " to " + transportParameters.getInitialMaxStreamDataUni());
        }
    }

    private long determineInitialMaxStreamData(QuicStream stream) {
        if (stream.isUnidirectional()) {
            return initialMaxStreamDataUni;
        }
        else if (stream.isClientInitiatedBidirectional()) {
            // Assuming client role, so for the receiver (imposing the limit) the stream is peer-initiated (remote).
            // "This limit applies to newly created bidirectional streams opened by the endpoint that receives
            // the transport parameter."
            // The client has received this transport parameter, so it applies to stream opened by the client.
            return initialMaxStreamDataBidiRemote;
        }
        else if (stream.isServerInitiatedBidirectional()) {
            // Assuming client role, so for the receiver (imposing the limit), the stream is locally-initiated
            // "This limit applies to newly created bidirectional streams opened by the endpoint that sends the
            // transport parameter."
            // The server has send this transport parameter, so it applies to streams opened by the server.
            return initialMaxStreamDataBidiLocal;
        }
        else {
            throw new ImplementationError();
        }
    }

    private long currentStreamCredits(QuicStream stream) {
        int streamId = stream.getStreamId();
        if (!maxStreamDataAllowed.containsKey(streamId)) {
            maxStreamDataAllowed.put(streamId, determineInitialMaxStreamData(stream));
            maxStreamDataAssigned.put(streamId, 0L);
        }

        long allowedByStream = maxStreamDataAllowed.get(streamId);
        long maxStreamIncrement = allowedByStream - maxStreamDataAssigned.get(streamId);
        long maxPossibleDataIncrement = maxDataAllowed - maxDataAssigned;
        if (maxStreamIncrement > maxPossibleDataIncrement) {
            maxStreamIncrement = maxPossibleDataIncrement;
        }
        return maxStreamIncrement;
    }


    public void process(MaxDataFrame frame) {
        synchronized (this) {
            // If frames are received out of order, the new max can be smaller than the current value.
            if (frame.getMaxData() > maxDataAllowed) {
                maxDataAllowed = frame.getMaxData();
            }
            this.notifyAll();
        }
    }

    public void process(MaxStreamDataFrame frame) {
        synchronized (this) {
            int streamId = frame.getStreamId();
            long maxStreamData = frame.getMaxData();
            // If frames are received out of order, the new max can be smaller than the current value.
            if (maxStreamData > maxStreamDataAllowed.get(streamId)) {
                maxStreamDataAllowed.put(streamId, maxStreamData);
            }
            this.notifyAll();
        }
    }
}

