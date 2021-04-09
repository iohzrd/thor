package io.dht;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.libp2p.core.PeerId;
import io.protos.dht.DhtProtos;

public class MessageSender {
    private static final String TAG = MessageSender.class.getSimpleName();
    private final PeerId p;
    private final KadDHT dht;

    private boolean invalid = false;

    public MessageSender(@NonNull PeerId p, @NonNull KadDHT dht) {
        this.p = p;
        this.dht = dht;
    }


    // streamReuseTries is the number of times we will try to reuse a stream to a
// given peer before giving up and reverting to the old one-message-per-stream
// behaviour.
    public static final int streamReuseTries = 3;

    private int singleMes = 0;

    public synchronized DhtProtos.Message SendRequest(@NonNull Closeable ctx,
                                                      @NonNull DhtProtos.Message pmes)
            throws ClosedException {


        // while (singleMes < streamReuseTries) {

        //singleMes++;

        if (ctx.isClosed()) {
            throw new ClosedException();
        }

                /*
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
                }*/


        try {
            return ctxReadMsg(ctx, pmes);
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, "error reading message error " +
                    throwable.getMessage());

        }
        //}

        throw new RuntimeException(); // TODO
    }


    @NonNull
    private DhtProtos.Message ctxReadMsg(Closeable ctx, DhtProtos.Message pmes) throws ClosedException, ExecutionException, InterruptedException {

        try {
            CompletableFuture<Object> ctrl = dht.host.newStream(
                    Collections.singletonList(KadDHT.Protocol), p).getController();
            LogUtils.info(TAG, "Success 1 " + KadDHT.Protocol + " " + p.toBase58());
            Object object = ctrl.get();
            LogUtils.info(TAG, "Success 2 " + KadDHT.Protocol + " " + p.toBase58());
            DhtProtocol.DhtController dhtController = (DhtProtocol.DhtController) object;
            LogUtils.info(TAG, "Success 3 " + KadDHT.Protocol + " " + p.toBase58());
            return dhtController.sendRequest(pmes).get();

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw throwable;
        } finally {
            LogUtils.error(TAG, "Success " + KadDHT.Protocol + " " + p.toBase58());
        }
        /*
        Stream s = ctrl.getStream().get(); // TODO
        LogUtils.error(TAG, "Success " + KadDHT.Protocol + " " + p.toBase58());
        AtomicReference<DhtProtos.Message> messageAtomicReference = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean(false);

        s.pushHandler(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                LogUtils.error(TAG, ctx.toString());
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                LogUtils.error(TAG, msg.toString());
            }
            @Override
            public boolean acceptInboundMessage(Object msg) throws Exception {
                return super.acceptInboundMessage(msg);
            }
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                    throws Exception {
                ctx.fireExceptionCaught(cause);
            }
        });
        /*
        s.pushHandler(new ProtocolMessageHandler<ByteBuf>() {
            @Override
            public void onMessage(@NotNull Stream stream, ByteBuf byteBuf) {
                try {
                    LogUtils.error(TAG, "success onMessage ");
                    DhtProtos.Message message = DhtProtos.Message.parseFrom(byteBuf.array());
                    messageAtomicReference.set(message);
                    LogUtils.error(TAG, "success pushHandler ");
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
                LogUtils.error(TAG, "Stream closed");
                done.set(true);
            }

            @Override
            public void onActivated(@NotNull Stream stream) {
                // TODO
            }

            @Override
            public void fireMessage(@NotNull Stream stream, @NotNull Object o) {
                LogUtils.error(TAG, "Fire message");
                // TODO
            }
        });*/
        /*
        s.writeAndFlush(pmes.toByteArray()); // TODO probably not working


        while (!done.get()) {
            if (ctx.isClosed()) {
                throw new ClosedException();
            }
        }
        DhtProtos.Message result = messageAtomicReference.get();
        if (result == null) {
            //throw new RuntimeException();
        }
        return result;*/

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
