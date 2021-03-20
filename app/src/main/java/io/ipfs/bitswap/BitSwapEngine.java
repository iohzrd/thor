package io.ipfs.bitswap;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.Closeable;
import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;
import io.protos.bitswap.BitswapProtos;

public class BitSwapEngine {
    public static final int MaxBlockSizeReplaceHasWithBlock = 1024;
    private static final String TAG = BitSwapEngine.class.getSimpleName();
    private final BlockStore blockstore;

    private final PeerID self;


    @NonNull
    private final BitSwapNetwork network;

    private BitSwapEngine(@NonNull BlockStore bs, @NonNull BitSwapNetwork network,
                          @NonNull PeerID self) {
        this.blockstore = bs;
        this.network = network;
        this.self = self;

    }

    // NewEngine creates a new block sending engine for the given block store
    public static BitSwapEngine NewEngine(@NonNull BlockStore bs, @NonNull BitSwapNetwork network, @NonNull PeerID self) {
        return new BitSwapEngine(bs, network, self);
    }

    private BitSwapMessage createMessage(@NonNull Task task) {

        // Create a new message
        BitSwapMessage msg = BitSwapMessage.New(false);

        LogUtils.verbose(TAG,
                "Bitswap process tasks" + " local " + self.String());

        // Amount of data in the request queue still waiting to be popped
        msg.SetPendingBytes(0);

        // Split out want-blocks, want-haves and DONT_HAVEs
        List<Cid> blockCids = new ArrayList<>();
        Map<Cid, TaskData> blockTasks = new HashMap<>();


        Cid c = task.Topic;
        TaskData td = task.Data;
        if (td.HaveBlock) {
            if (td.IsWantBlock) {
                blockCids.add(c);
                blockTasks.put(c, td);
            } else {
                // Add HAVES to the message
                msg.AddHave(c);
            }
        } else {
            // Add DONT_HAVEs to the message
            msg.AddDontHave(c);
        }


        Map<Cid, Block> blks = getBlocks(blockCids);
        for (Map.Entry<Cid, TaskData> entry : blockTasks.entrySet()) {
            Block blk = blks.get(entry.getKey());
            // If the block was not found (it has been removed)
            if (blk == null) {
                // If the client requested DONT_HAVE, add DONT_HAVE to the message
                if (entry.getValue().SendDontHave) {
                    msg.AddDontHave(entry.getKey());
                }
            } else {
                LogUtils.error(TAG, "Block added to message " + blk.Cid().String());
                msg.AddBlock(blk);
            }
        }
        return msg;

    }

    // Split the want-have / want-block entries from the cancel entries
    public Pair<List<BitSwapMessage.Entry>, List<BitSwapMessage.Entry>> splitWantsCancels(
            @NonNull List<BitSwapMessage.Entry> es) {
        List<BitSwapMessage.Entry> wants = new ArrayList<>();
        List<BitSwapMessage.Entry> cancels = new ArrayList<>();
        for (BitSwapMessage.Entry et : es) {
            if (et.Cancel) {
                cancels.add(et);
            } else {
                wants.add(et);
            }
        }
        return Pair.create(wants, cancels);
    }


    public void MessageReceived(@NonNull PeerID peer, @NonNull Protocol protocol, @NonNull BitSwapMessage m) {

        List<BitSwapMessage.Entry> entries = m.Wantlist();

        if (entries.size() > 0) {
            for (BitSwapMessage.Entry et : entries) {
                if (!et.Cancel) {

                    if (et.WantType == BitswapProtos.Message.Wantlist.WantType.Have) {
                        LogUtils.verbose(TAG,
                                "Bitswap engine <- want-have" +
                                        "  local " + self.String() + " from " + peer.String()
                                        + " cid " + et.Cid.String());
                    } else {
                        LogUtils.verbose(TAG,
                                "Bitswap engine <- want-block" +
                                        "  local " + self.String() + " from " + peer.String()
                                        + " cid " + et.Cid.String());
                    }
                }
            }
        }

        if (m.Empty()) {
            LogUtils.info(TAG, "received empty message from " + peer);
        }


        // Get block sizes
        Pair<List<BitSwapMessage.Entry>, List<BitSwapMessage.Entry>> result = splitWantsCancels(entries);
        List<BitSwapMessage.Entry> wants = result.first;
        List<BitSwapMessage.Entry> cancels = result.second;

        Set<Cid> wantKs = new HashSet<>();
        for (BitSwapMessage.Entry entry : wants) {
            wantKs.add(entry.Cid);
        }


        HashMap<Cid, Integer> blockSizes = getBlockSizes(wantKs);


        // Remove cancelled blocks from the queue
        for (BitSwapMessage.Entry entry : cancels) {
            LogUtils.verbose(TAG, "Bitswap engine <- cancel " + " local " +
                    self + " from " + peer.String() + " cid " + entry.Cid.String());
            // TODO handle cancels
        }


        for (BitSwapMessage.Entry entry : wants) {
            // For each want-have / want-block

            Cid c = entry.Cid;
            Integer blockSize = blockSizes.get(entry.Cid);


            // If the block was not found
            if (blockSize == null) {
                LogUtils.verbose(TAG,
                        "Bitswap engine: block not found" + " local " + self.String()
                                + " from " + peer.String() + " cid " + entry.Cid.String()
                                + " sendDontHave " + entry.SendDontHave);

                // Only add the task to the queue if the requester wants a DONT_HAVE
                if (IPFS.SEND_DONT_HAVES && entry.SendDontHave) {

                    boolean isWantBlock = false;
                    if (entry.WantType == BitswapProtos.Message.Wantlist.WantType.Block) {
                        isWantBlock = true;
                    }

                    Task task = new Task(c,
                            new TaskData(false, isWantBlock, entry.SendDontHave));
                    BitSwapMessage msg = createMessage(task);
                    if (!msg.Empty()) {
                        // TODO closable
                        Closeable closeable = () -> false;
                        try {
                            network.WriteMessage(closeable, peer, protocol, msg);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }
                }
            } else {
                // The block was found, add it to the queue

                boolean isWantBlock = sendAsBlock(entry.WantType, blockSize);

                LogUtils.verbose(TAG,
                        "Bitswap engine: block found" + "local" + self.String() +
                                "from " + peer + " cid " + entry.Cid.String()
                                + " isWantBlock " + isWantBlock);


                Task task = new Task(c, new TaskData(true, isWantBlock, entry.SendDontHave));
                BitSwapMessage msg = createMessage(task);
                if (!msg.Empty()) {
                    // TODO closable
                    Closeable closeable = () -> false;
                    try {
                        network.WriteMessage(closeable, peer, protocol, msg);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }

            }
        }
    }

    private boolean sendAsBlock(BitswapProtos.Message.Wantlist.WantType wantType, Integer blockSize) {
        boolean isWantBlock = wantType == BitswapProtos.Message.Wantlist.WantType.Block;
        return isWantBlock || blockSize <= MaxBlockSizeReplaceHasWithBlock;
    }


    public Map<Cid, Block> getBlocks(@NonNull List<Cid> cids) {
        Map<Cid, Block> blks = new HashMap<>();
        for (Cid c : cids) {
            Block block = blockstore.Get(c);
            if (block != null) {
                blks.put(c, block);
            }
        }
        return blks;
    }

    public HashMap<Cid, Integer> getBlockSizes(@NonNull Set<Cid> wantKs) {

        HashMap<Cid, Integer> blocksizes = new HashMap<>();
        for (Cid cid : wantKs) {
            int size = blockstore.GetSize(cid);
            if (size > 0) {
                blocksizes.put(cid, size);
            }
        }
        return blocksizes;
    }


    private static class Task {
        // Topic for the task
        public final Cid Topic;
        // Arbitrary data associated with this Task by the client
        public final TaskData Data;

        public Task(@NonNull Cid topic, @NonNull TaskData data) {
            this.Topic = topic;
            this.Data = data;
        }

    }


    private static class TaskData {
        // Tasks can be want-have or want-block
        final boolean IsWantBlock;
        // Whether to immediately send a response if the block is not found
        final boolean SendDontHave;
        // Whether the block was found
        final boolean HaveBlock;

        public TaskData(boolean haveBlock, boolean isWantBlock, boolean sendDontHave) {
            this.SendDontHave = sendDontHave;
            this.IsWantBlock = isWantBlock;
            this.HaveBlock = haveBlock;
        }


    }

}
