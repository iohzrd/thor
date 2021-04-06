package io.dht;

import androidx.annotation.NonNull;

import com.google.protobuf.InvalidProtocolBufferException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import io.Closeable;
import io.LogUtils;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.protocol.ProtocolMessageHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
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

    private int singleMes;
    public DhtProtos.Message SendRequest(@NonNull Closeable ctx, @NonNull DhtProtos.Message pmes) {
        
        boolean retry = false;
        while(true) {
            try {
                prep(ctx);
            } catch (Throwable throwable){
                throw new RuntimeException(throwable);
            }

            // TODO ctx is closed throw exception
            
            try {
                writeMsg(pmes);
            } catch (Throwable throwable){
                try {
                    s.reset().get();
                } catch (Throwable ignore){
                    // TOOD ignore
                }
                s = null;
                if(retry){
                    LogUtils.error(TAG, "error writing message error " + throwable.getMessage());
                    return null;
                }
                LogUtils.error(TAG,"error writing message error " +
                        throwable.getMessage()+ " retrying  true");
                retry = true;
                continue;
            }


            DhtProtos.Message mes = DhtProtos.Message.getDefaultInstance();
            try {
                ctxReadMsg(ctx, mes);
            } catch (Throwable throwable){

                try {
                    s.reset().get();
                } catch (Throwable ignore){
                    // TOOD ignore
                }
                s = null;

                if(retry) {
                    LogUtils.error(TAG, "error reading message error " + throwable.getMessage());
                    return null;
                }
                LogUtils.error(TAG,"error reading message error " +
                        throwable.getMessage()+ " retrying  true");
                retry = true;
                continue;
            }


            if( singleMes > streamReuseTries) {
                try {
                    s.close().get();
                } catch (Throwable ignore){
                    // TODO
                }
                s = null;
            } else if(retry) {
                singleMes++;
            }

            return mes;
        }
    }

    private void writeMsg(DhtProtos.Message pmes) {
        s.writeAndFlush(pmes.toByteArray()); // TODO probably not working
        // TODO close writing evt. s.closeWrite().get()
        // TODO  writeMsg(s, pmes);
    }


    private void ctxReadMsg(Closeable ctx, DhtProtos.Message mes) {

        // TODO
        s.pushHandler(new ProtocolMessageHandler<ByteBuf>() {
            @Override
            public void onMessage(@NotNull Stream stream, ByteBuf byteBuf) {
                try{
                    DhtProtos.Message message = DhtProtos.Message.parseFrom(byteBuf.array());
                } catch (InvalidProtocolBufferException e) {
                    LogUtils.error(TAG, e);
                }
            }


            @Override
            public void onException(@Nullable Throwable throwable) {

            }

            @Override
            public void onClosed(@NotNull Stream stream) {

            }

            @Override
            public void onActivated(@NotNull Stream stream) {

            }

            @Override
            public void fireMessage(@NotNull Stream stream, @NotNull Object o) {

            }
        });
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
