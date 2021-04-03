package io.ipfs.bitswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.Closeable;
import io.LogUtils;
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
    @NonNull
    private final ContentRouting contentRouting;
    private final List<String> protocols = new ArrayList<>();

    private LiteHost(@NonNull Host host,
                     @NonNull ContentRouting contentRouting,
                     @NonNull List<String> protos) {
        this.host = host;
        this.contentRouting = contentRouting;
        this.protocols.addAll(protos);
    }

    public static BitSwapNetwork NewLiteHost(@NonNull Host host,
                                             @NonNull ContentRouting contentRouting,
                                             @NonNull List<String> protocols) {
        return new LiteHost(host, contentRouting, protocols);
    }

    @Override
    public boolean ConnectTo(@NonNull Closeable closeable, @NonNull PeerId peer, boolean protect) throws ClosedException {
        try {
            CompletableFuture<Connection> future = host.getNetwork().connect(peer);
            // TODO closeable
            return future.get() != null;
        } catch (Throwable throwable) {
            if(closeable.isClosed()){
                throw new ClosedException();
            }
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
    public Set<PeerId> getPeers() {
        Set<PeerId> peerIds = new HashSet<>();
        for (Connection connection: host.getNetwork().getConnections()){
            peerIds.add(connection.secureSession().getRemoteId());
        }

        return peerIds;
    }


    @Override
    public void WriteMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                             @NonNull BitSwapMessage message, int timeout) throws ClosedException, ProtocolNotSupported {

        try {
            byte[] data = message.ToNetV1();
            StreamPromise<Object> promise = host.newStream(protocols, peer);
            Stream stream = promise.getStream().get();
            stream.writeAndFlush(data);
            stream.close();
            /*
            long res = host.WriteMessage(closeable, peer, protocols, data, timeout);
            if (Objects.equals(data.length, res)) {
                throw new RuntimeException("Message not fully written");
            }*/
        } catch (Throwable throwable) {
           throw new RuntimeException(throwable);
        }
    }



    @Override
    public void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int number) throws ClosedException {
        contentRouting.FindProvidersAsync(providers, cid, number);
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {
        throw new RuntimeException("not supported");
    }
}
