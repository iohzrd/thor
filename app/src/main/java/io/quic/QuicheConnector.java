package io.quic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.incubator.codec.quic.DirectIoByteBufAllocator;
import io.netty.incubator.codec.quic.Quic;
import io.netty.incubator.codec.quic.QuicConnectionAddress;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.Quiche;
import io.netty.incubator.codec.quic.QuicheConfig;
import io.netty.incubator.codec.quic.QuicheQuicConnection;
import io.netty.incubator.codec.quic.QuicheQuicSslEngine;

public class QuicheConnector {


    @Nullable
    public static QuicheQuicConnection connect(@NonNull QuicSslContext sslContext,
                                               @NonNull QuicheConfig config,
                                               @NonNull InetSocketAddress remote) throws IOException {

        QuicheQuicSslEngine engine = (QuicheQuicSslEngine) sslContext.newEngine(null);


        QuicheQuicConnection connection = connect(engine, config, Quiche.QUICHE_MAX_CONN_ID_LEN);

        boolean success = connectionSendSimple(connection.address(), remote);
        if (!success) {
            return null;
        }
        return connection;
    }


    public static QuicheQuicConnection connect(
            QuicheQuicSslEngine engine, QuicheConfig config, int localConnIdLength) {

        QuicConnectionAddress address = QuicConnectionAddress.random(localConnIdLength);

        if (!engine.getUseClientMode()) {
            throw new IllegalArgumentException("QuicSslEngine is not create in client mode");
        }
        ByteBuffer connectId = address.connId.duplicate();
        ByteBuf idBuffer = new DirectIoByteBufAllocator(ByteBufAllocator.DEFAULT).
                directBuffer(connectId.remaining()).writeBytes(connectId.duplicate());
        try {
            return ((QuicheQuicSslEngine) engine).createConnection(ssl ->
                    io.netty.incubator.codec.quic.Quiche.quiche_conn_new_with_tls(
                            io.netty.incubator.codec.quic.Quiche.memoryAddress(idBuffer)
                                    + idBuffer.readerIndex(),
                            idBuffer.readableBytes(), -1, -1,
                            config.nativeAddress(), ssl,
                            false));
        } finally {
            idBuffer.release();
        }
    }

    /**
     * Write datagrams if needed and return {@code true} if something was written and we need to call
     * {@link Channel#flush()} at some point.
     */
    /*
    private boolean connectionSend(InetSocketAddress remote) {
        if (isConnDestroyed() || inConnectionSend) {
            return false;
        }

        inConnectionSend = true;
        try {
            boolean packetWasWritten;
            SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator =
                    config.getSegmentedDatagramPacketAllocator();
            if (segmentedDatagramPacketAllocator.maxNumSegments() > 0) {
                packetWasWritten = connectionSendSegments(segmentedDatagramPacketAllocator);
            } else {
                packetWasWritten = connectionSendSimple(remote);
            }
            if (packetWasWritten) {
                timeoutHandler.scheduleTimeout();
            }
            return packetWasWritten;
        } finally {
            inConnectionSend = false;
        }
    }*/
    private static boolean connectionSendSimple(long connection, InetSocketAddress remote) throws IOException {

        boolean packetWasWritten = false;
        for (; ; ) {
            ByteBuf out = new DirectIoByteBufAllocator(ByteBufAllocator.DEFAULT)
                    .directBuffer(Quic.MAX_DATAGRAM_SIZE);
            int writerIndex = out.writerIndex();
            int written = Quiche.quiche_conn_send(
                    connection, Quiche.memoryAddress(out) + writerIndex, out.writableBytes());

            try {
                if (Quiche.throwIfError(written)) {
                    out.release();
                    break;
                }
            } catch (Exception e) {
                out.release();
                break;
            }

            if (written == 0) {
                // No need to create a new datagram packet. Just release and try again.
                out.release();
                continue;
            }
            out.writerIndex(writerIndex + written);
            Socket socket = new Socket(remote.getAddress(), remote.getPort());
            // TODO write on socket
            //socket.getOutputStream().write(out.array());
            InputStream inputStream = socket.getInputStream();

            //while

            //socket.write(new DatagramPacket(out, remote));
            packetWasWritten = true;
        }
        return packetWasWritten;
    }
}
