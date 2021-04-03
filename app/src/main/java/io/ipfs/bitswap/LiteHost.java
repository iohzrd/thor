package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.Closeable;
import io.dht.ContentRouting;
import io.dht.Providers;
import io.ipfs.ClosedException;
import io.ipfs.ProtocolNotSupported;
import io.ipfs.cid.Cid;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.P2PChannel;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.StreamPromise;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolDescriptor;


public class LiteHost implements BitSwapNetwork {

    @NonNull
    private final Host host;
    @Nullable
    private final ContentRouting contentRouting;
    private final List<String> protocols = new ArrayList<>();

    private LiteHost(@NonNull Host host,
                     @Nullable ContentRouting contentRouting,
                     @NonNull List<String> protos) {
        this.host = host;
        this.contentRouting = contentRouting;
        this.protocols.addAll(protos);
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @Nullable ContentRouting contentRouting,
                                             @NonNull List<String> protocols) {
        return new LiteHost(host, contentRouting, protocols);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peer, boolean protect) throws ClosedException {
        try {
            CompletableFuture<Connection> future = host.getNetwork().connect(peer);
            // TODO
            return future.get() != null;
        } catch (Throwable throwable) {
            return false;
        }

    }


    @Override
    public void SetDelegate(@NonNull Receiver receiver) {


        host.addProtocolHandler(new ProtocolBinding<Object>() {
            @NotNull
            @Override
            public CompletableFuture<?> initChannel(@NotNull P2PChannel p2PChannel, @NotNull String s) {

                return null;
            }

            @NotNull
            @Override
            public ProtocolDescriptor getProtocolDescriptor() {
                return new ProtocolDescriptor(protocols);
            }
        });


            /* TODO
            host.SetStreamHandler(protocol, new StreamHandler() {
                @Override
                public boolean gate(@NonNull PeerID peerID) {
                    return receiver.GatePeer(peerID);
                }

                @Override
                public void error(@NonNull PeerID peerID, @NonNull Protocol protocol, @NonNull String error) {
                    receiver.ReceiveError(peerID, protocol, error);
                }

                @Override
                public void message(@NonNull PeerID peerID, @NonNull Protocol protocol, @NonNull byte[] data) {
                    try {
                        BitSwapMessage received = BitSwapMessage.fromData(data);
                        receiver.ReceiveMessage(peerID, protocol, received);
                    } catch (Throwable throwable) {
                        receiver.ReceiveError(peerID, protocol, "Redirect : " + throwable.getMessage());
                    }
                }

            });*/


    }

    @NonNull
    @Override
    public List<PeerId> getPeers() {
        // TODO
        return new ArrayList<>();//host.getPeers();
    }


    @Override
    public void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message, int timeout) throws ClosedException, ProtocolNotSupported {

        try {
            byte[] data = message.ToNetV1();
            StreamPromise<Object> stream = host.newStream(protocols, peer);
            Stream st = stream.getStream().get();

            st.writeAndFlush(data);
            st.close();
            /*
            long res = host.WriteMessage(closeable, peer, protocols, data, timeout);
            if (Objects.equals(data.length, res)) {
                throw new RuntimeException("Message not fully written");
            }*/
        } catch (Throwable throwable) {

        }
    }



    @Override
    public void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int number) throws ClosedException {
        if (contentRouting != null) {
            contentRouting.FindProvidersAsync(providers, cid, number);
        }
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        throw new RuntimeException("not supported");
    }
}
