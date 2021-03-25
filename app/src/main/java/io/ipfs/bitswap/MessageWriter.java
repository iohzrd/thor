package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.List;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.ProtocolNotSupported;
import io.ipfs.cid.Cid;
import io.libp2p.peer.PeerID;
import io.protos.bitswap.BitswapProtos;

public class MessageWriter {

    public static final int MaxPriority = Integer.MAX_VALUE;


    public static void sendHaveMessage(@NonNull Closeable closeable,
                                       @NonNull BitSwapNetwork network,
                                       @NonNull PeerID peer,
                                       @NonNull List<Cid> wantHaves,
                                       int timeout)
            throws ClosedException, ProtocolNotSupported {
        if (wantHaves.size() == 0) {
            return;
        }

        int priority = MaxPriority;

        BitSwapMessage message = BitSwapMessage.New(false);

        for (Cid c : wantHaves) {

            // Broadcast wants are sent as want-have
            BitswapProtos.Message.Wantlist.WantType wantType =
                    BitswapProtos.Message.Wantlist.WantType.Have;

            message.AddEntry(c, priority, wantType, false);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        network.WriteMessage(closeable, peer, message, timeout);


    }

    public static void sendWantsMessage(@NonNull Closeable closeable,
                                        @NonNull BitSwapNetwork network,
                                        @NonNull PeerID peer,
                                        @NonNull List<Cid> wantBlocks,
                                        int timeout)
            throws ClosedException, ProtocolNotSupported {

        if (wantBlocks.size() == 0) {
            return;
        }
        BitSwapMessage message = BitSwapMessage.New(false);

        int priority = MaxPriority;

        for (Cid c : wantBlocks) {

            message.AddEntry(c, priority,
                    BitswapProtos.Message.Wantlist.WantType.Block, true);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        network.WriteMessage(closeable, peer, message, timeout);

    }


}
