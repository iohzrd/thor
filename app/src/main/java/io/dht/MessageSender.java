package io.dht;

import androidx.annotation.NonNull;

import com.google.protobuf.InvalidProtocolBufferException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.protocol.ProtocolMessageHandler;
import io.netty.buffer.ByteBuf;
import io.protos.dht.DhtProtos;

public class MessageSender {
    private static final String TAG = MessageSender.class.getSimpleName();
    private final PeerId p;
    private final KadDHT dht;
    private Stream s;
    private boolean invalid = false;

    public MessageSender(@NonNull PeerId p, @NonNull KadDHT dht) {
        this.p = p;
        this.dht = dht;
    }

    public void prepOrInvalidate(@NonNull Closeable ctx) {
        try {
            prep(ctx);
        } catch (Throwable throwable){
            invalidate();
            throw new RuntimeException(throwable);
        }
    }

    private void prep(@NonNull Closeable ctx) throws ExecutionException, InterruptedException {
        if(invalid){
            throw new RuntimeException("message sender has been invalidated");
        }
        if(s != null){
            return;
        }
        StreamPromise<Object> promise = dht.host.newStream(Collections.singletonList(KadDHT.Protocol), p); // TODO
        s = promise.getStream().get(); // TODO
    }


    // invalidate is called before this messageSender is removed from the strmap.
// It prevents the messageSender from being reused/reinitialized and then
// forgotten (leaving the stream open).
    private void invalidate() {

        invalid = true;
        if( s != null) {
            try {
                s.reset().get();
            } catch (ExecutionException | InterruptedException e) {
                LogUtils.error(TAG, e);
            }
            s = null;
        }

    }


    // streamReuseTries is the number of times we will try to reuse a stream to a
// given peer before giving up and reverting to the old one-message-per-stream
// behaviour.
    public static final int streamReuseTries = 3;

    private int singleMes = 0;

    public DhtProtos.Message SendRequest(@NonNull Closeable ctx, @NonNull DhtProtos.Message pmes) throws ClosedException {

        boolean retry = false;
        while (true) {

            try {
                prep(ctx);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }


            if (singleMes > streamReuseTries) {
                singleMes++;

                if (ctx.isClosed()) {
                    throw new ClosedException();
                }

                try {
                    writeMsg(pmes);
                } catch (Throwable throwable) {
                    try {
                        s.reset().get();
                    } catch (Throwable ignore) {
                        // TOOD ignore
                    }
                    s = null;
                    if (retry) {
                        LogUtils.error(TAG, "error writing message error " + throwable.getMessage());
                        return null;
                    }
                    LogUtils.error(TAG, "error writing message error " +
                            throwable.getMessage() + " retrying  true");
                    retry = true;
                    continue;
                }


                try {
                    return ctxReadMsg(ctx);
                } catch (ClosedException exception) {
                    throw exception;
                } catch (Throwable throwable) {

                    try {
                        s.reset().get();
                    } catch (Throwable ignore) {
                        // TOOD ignore
                    }
                    s = null;

                    if (retry) {
                        LogUtils.error(TAG, "error reading message error " + throwable.getMessage());
                        return null;
                    }
                    LogUtils.error(TAG, "error reading message error " +
                            throwable.getMessage() + " retrying  true");
                    retry = true;
                }
            }
        }
    }

    private void writeMsg(DhtProtos.Message pmes) {
        s.writeAndFlush(pmes.toByteArray()); // TODO probably not working
        // TODO close writing evt. s.closeWrite().get()
        // TODO  writeMsg(s, pmes);
    }


    @NonNull
    private DhtProtos.Message ctxReadMsg(Closeable ctx) throws ClosedException {
        AtomicReference<DhtProtos.Message> messageAtomicReference = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean(false);
        // TODO
        s.pushHandler(new ProtocolMessageHandler<ByteBuf>() {
            @Override
            public void onMessage(@NotNull Stream stream, ByteBuf byteBuf) {
                try {
                    DhtProtos.Message message = DhtProtos.Message.parseFrom(byteBuf.array());
                    messageAtomicReference.set(message);
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                } finally {
                    done.set(true);
                    // TODO s.close();
                }
            }


            @Override
            public void onException(@Nullable Throwable throwable) {
                LogUtils.error(TAG, throwable);
                done.set(true);
            }

            @Override
            public void onClosed(@NotNull Stream stream) {
                done.set(true);
            }

            @Override
            public void onActivated(@NotNull Stream stream) {
                // TODO
            }

            @Override
            public void fireMessage(@NotNull Stream stream, @NotNull Object o) {
                // TODO
            }
        });

        while (!done.get()) {
            if (ctx.isClosed()) {
                throw new ClosedException();
            }
        }
        DhtProtos.Message result = messageAtomicReference.get();
        if (result == null) {
            throw new RuntimeException();
        }
        return result;

        /* TODO
        errc := make(chan error, 1)
        go func(r msgio.ReadCloser) {
            defer close(errc)
                    bytes, err := r.ReadMsg()
            defer r.ReleaseMsg(bytes)
            if err != nil {
                errc <- err
                return
            }
            errc <- mes.Unmarshal(bytes)
        }(ms.r)

                t := time.NewTimer(dhtReadMessageTimeout)
        defer t.Stop()

        select {
            case err := <-errc:
                return err
            case <-ctx.Done():
                return ctx.Err()
            case <-t.C:
                return ErrReadTimeout
        }*/
    }

}
