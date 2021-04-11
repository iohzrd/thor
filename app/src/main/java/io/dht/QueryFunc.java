package io.dht;

import androidx.annotation.NonNull;

import java.util.List;

import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionFailure;
import io.core.ConnectionTimeout;
import io.core.ProtocolNotSupported;
import io.libp2p.AddrInfo;
import io.libp2p.core.PeerId;

public interface QueryFunc {
    @NonNull
    List<AddrInfo> func(@NonNull Closeable ctx, @NonNull PeerId peerId)
            throws ClosedException, ProtocolNotSupported, ConnectionFailure, ConnectionTimeout;
}
