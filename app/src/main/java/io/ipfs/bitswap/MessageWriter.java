package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.List;

import bitswap.pb.MessageOuterClass;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.host.PeerId;


public class MessageWriter {
    public static final int MaxPriority = Integer.MAX_VALUE;
    private static final String TAG = MessageWriter.class.getSimpleName();

    public static void sendHaveMessage(@NonNull Closeable closeable,
                                       @NonNull BitSwap network,
                                       @NonNull PeerId peer,
                                       @NonNull List<Cid> wantHaves)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {
        if (wantHaves.size() == 0) {
            return;
        }

        int priority = MaxPriority;

        BitSwapMessage message = BitSwapMessage.New(false);

        for (Cid c : wantHaves) {

            // Broadcast wants are sent as want-have
            MessageOuterClass.Message.Wantlist.WantType wantType =
                    MessageOuterClass.Message.Wantlist.WantType.Have;

            message.AddEntry(c, priority, wantType, false);

            priority--;
        }

        if (message.Empty()) {
            return;
        }
        LogUtils.debug(TAG, "send HAVE Message " + peer.toBase58());
        network.writeMessage(closeable, peer, message, IPFS.PRIORITY_URGENT);


    }

    public static void sendWantsMessage(@NonNull Closeable closeable,
                                        @NonNull BitSwap network,
                                        @NonNull PeerId peer,
                                        @NonNull List<Cid> wantBlocks)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        if (wantBlocks.size() == 0) {
            return;
        }
        BitSwapMessage message = BitSwapMessage.New(false);

        int priority = MaxPriority;

        for (Cid c : wantBlocks) {

            message.AddEntry(c, priority,
                    MessageOuterClass.Message.Wantlist.WantType.Block, true);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        LogUtils.debug(TAG, "send WANT Message " + peer.toBase58());
        network.writeMessage(closeable, peer, message, IPFS.PRIORITY_URGENT);

    }


}
