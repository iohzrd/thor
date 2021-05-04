package io.ipfs.relay;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.util.Objects;

import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.host.Connection;
import io.ipfs.host.LiteHost;
import io.libp2p.core.PeerId;

public class Relay {
    private final LiteHost host;

    public Relay(@NonNull LiteHost host) {
        this.host = host;
    }


    public boolean canHop(@NonNull Closeable closeable, @NonNull PeerId peerId)
            throws ConnectionIssue, ClosedException {


        relay.pb.Relay.CircuitRelay message = relay.pb.Relay.CircuitRelay.newBuilder()
                .setType(relay.pb.Relay.CircuitRelay.Type.CAN_HOP)
                .build();
        Connection conn = host.connect(closeable, peerId);
        try {
            synchronized (peerId.toBase58().intern()) {

                MessageLite messageLite = host.request(closeable, RelayProtocol.Protocol, conn, message);
                Objects.requireNonNull(messageLite);
                relay.pb.Relay.CircuitRelay msg = (relay.pb.Relay.CircuitRelay) messageLite;
                Objects.requireNonNull(msg);
                return msg.getType() == relay.pb.Relay.CircuitRelay.Type.STATUS;
            }
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
