package threads.lite.bitswap;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bitswap.pb.MessageOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.TimeoutCloseable;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;


public class BitSwapEngine {
    public static final int MaxBlockSizeReplaceHasWithBlock = 1024;
    private static final String TAG = BitSwapEngine.class.getSimpleName();
    private final BlockStore blockstore;

    private final PeerId self;


    @NonNull
    private final BitSwap bitSwap;

    BitSwapEngine(@NonNull BitSwap bitSwap,
                  @NonNull BlockStore bs,

                  @NonNull PeerId self) {
        this.bitSwap = bitSwap;
        this.blockstore = bs;
        this.self = self;

    }

    private BitSwapMessage createMessage(@NonNull Task task) {

        // Create a new message
        BitSwapMessage msg = BitSwapMessage.New(false);

        LogUtils.verbose(TAG,
                "Bitswap process tasks" + " local " + self.toBase58());

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
                LogUtils.error(TAG, "Block added to message " + blk.getCid().String());
                msg.AddBlock(blk);
            }
        }
        return msg;

    }

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


    public void MessageReceived(@NonNull PeerId peer, @NonNull BitSwapMessage m) {

        List<BitSwapMessage.Entry> entries = m.Wantlist();

        if (entries.size() > 0) {
            for (BitSwapMessage.Entry et : entries) {
                if (!et.Cancel) {

                    if (et.WantType == MessageOuterClass.Message.Wantlist.WantType.Have) {
                        LogUtils.verbose(TAG,
                                "Bitswap engine <- want-have" +
                                        "  local " + self.toBase58() + " from " + peer.toBase58()
                                        + " cid " + et.Cid.String());
                    } else {
                        LogUtils.verbose(TAG,
                                "Bitswap engine <- want-block" +
                                        "  local " + self.toBase58() + " from " + peer.toBase58()
                                        + " cid " + et.Cid.String());
                    }
                }
            }
        }

        if (m.Empty()) {
            LogUtils.info(TAG, "received empty message from " + peer);
        }


        Pair<List<BitSwapMessage.Entry>, List<BitSwapMessage.Entry>> result = splitWantsCancels(entries);
        List<BitSwapMessage.Entry> wants = result.first;


        Set<Cid> wantKs = new HashSet<>();
        for (BitSwapMessage.Entry entry : wants) {
            wantKs.add(entry.Cid);
        }


        HashMap<Cid, Integer> blockSizes = getBlockSizes(wantKs);


        for (BitSwapMessage.Entry entry : wants) {
            // For each want-have / want-block

            Cid c = entry.Cid;
            Integer blockSize = blockSizes.get(entry.Cid);

            if (blockSize == null) {
                LogUtils.verbose(TAG,
                        "Bitswap engine: block not found" + " local " + self.toBase58()
                                + " from " + peer.toBase58() + " cid " + entry.Cid.String()
                                + " sendDontHave " + entry.SendDontHave);

                // Only add the task to the queue if the requester wants a DONT_HAVE
                if (IPFS.SEND_DONT_HAVES && entry.SendDontHave) {

                    boolean isWantBlock = false;
                    if (entry.WantType == MessageOuterClass.Message.Wantlist.WantType.Block) {
                        isWantBlock = true;
                    }

                    Task task = new Task(c,
                            new TaskData(false, isWantBlock, entry.SendDontHave));
                    BitSwapMessage msg = createMessage(task);
                    if (!msg.Empty()) {
                        try {
                            bitSwap.writeMessage(new TimeoutCloseable(5), peer, msg, IPFS.PRIORITY_NORMAL);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }
                }
            } else {
                boolean isWantBlock = sendAsBlock(entry.WantType, blockSize);

                LogUtils.verbose(TAG,
                        "Bitswap engine: block found" + "local" + self.toBase58() +
                                "from " + peer + " cid " + entry.Cid.String()
                                + " isWantBlock " + isWantBlock);

                Task task = new Task(c, new TaskData(true, isWantBlock, entry.SendDontHave));
                BitSwapMessage msg = createMessage(task);
                if (!msg.Empty()) {
                    try {
                        bitSwap.writeMessage(new TimeoutCloseable(10), peer, msg, IPFS.PRIORITY_NORMAL);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            }
        }
    }

    private boolean sendAsBlock(MessageOuterClass.Message.Wantlist.WantType wantType, Integer blockSize) {
        boolean isWantBlock = wantType == MessageOuterClass.Message.Wantlist.WantType.Block;
        return isWantBlock || blockSize <= MaxBlockSizeReplaceHasWithBlock;
    }


    public Map<Cid, Block> getBlocks(@NonNull List<Cid> cids) {
        Map<Cid, Block> blks = new HashMap<>();
        for (Cid c : cids) {
            Block block = blockstore.getBlock(c);
            if (block != null) {
                blks.put(c, block);
            }
        }
        return blks;
    }

    public HashMap<Cid, Integer> getBlockSizes(@NonNull Set<Cid> wantKs) {

        HashMap<Cid, Integer> sizes = new HashMap<>();
        for (Cid cid : wantKs) {
            int size = blockstore.getSize(cid);
            if (size > 0) {
                sizes.put(cid, size);
            }
        }
        return sizes;
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
