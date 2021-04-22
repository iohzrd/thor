package io.ipfs.relay;

import androidx.annotation.NonNull;

import java.util.concurrent.CompletableFuture;

import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.ipfs.LiteHost;
import io.libp2p.HostBuilder;
import io.libp2p.core.Connection;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;

public class Relay {
    private final LiteHost host;

    public Relay(@NonNull LiteHost host){
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

                Object object =  host.stream(closeable, RelayProtocol.Protocol, conn);

                RelayProtocol.RelayController controller = (RelayProtocol.RelayController) object;
                CompletableFuture<relay.pb.Relay.CircuitRelay> ctrl = controller.canHop(message);


                while (!ctrl.isDone()) {
                    if (closeable.isClosed()) {
                        ctrl.cancel(true);
                    }
                }

                if (closeable.isClosed()) {
                    throw new ClosedException();
                }
                relay.pb.Relay.CircuitRelay msg = ctrl.get();
                return msg.getType() == relay.pb.Relay.CircuitRelay.Type.STATUS;


            }
        } catch (ClosedException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
