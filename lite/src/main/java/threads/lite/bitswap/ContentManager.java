package threads.lite.bitswap;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import bitswap.pb.MessageOuterClass;
import threads.lite.IPFS;
import threads.lite.LogUtils;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.core.ConnectionIssue;
import threads.lite.core.ProtocolIssue;
import threads.lite.core.TimeoutIssue;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;
import threads.lite.host.LiteHost;


public class ContentManager {

    private static final String TAG = ContentManager.class.getSimpleName();

    private final LiteHost host;
    private final BlockStore blockStore;
    private final ExecutorService providers = Executors.newFixedThreadPool(8);
    private final ConcurrentSkipListSet<PeerId> whitelist = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerId> priority = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<Cid> loads = new ConcurrentSkipListSet<>();
    private final ConcurrentHashMap<Cid, ConcurrentLinkedDeque<PeerId>> matches = new ConcurrentHashMap<>();
    private final Blocker blocker = new Blocker();
    private final BitSwap bitSwap;

    public ContentManager(@NonNull BitSwap bitSwap, @NonNull BlockStore blockStore, @NonNull LiteHost host) {
        this.bitSwap = bitSwap;
        this.blockStore = blockStore;
        this.host = host;
    }


    public void haveReceived(@NonNull PeerId peer, @NonNull List<Cid> cids) {

        for (Cid cid : cids) {

            ConcurrentLinkedDeque<PeerId> res = matches.get(cid);
            if (res != null) {
                res.add(peer);

                LogUtils.info(TAG, "HaveReceived " + cid.String() + " " + peer.toBase58());

                priority.add(peer);

            }
        }
    }

    public void reset() {

        LogUtils.verbose(TAG, "Reset");
        try {
            loads.clear();
            priority.clear();
            whitelist.clear();
            matches.clear();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void runHaveMessage(@NonNull Closeable closeable, @NonNull PeerId peer,
                               @NonNull List<Cid> cids) {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean success = false;
            try {
                if (closeable.isClosed()) {
                    return;
                }
                sendHaveMessage(closeable, peer, cids);
                success = true;
                whitelist.add(peer);
            } catch (ClosedException | TimeoutIssue ignore) {
                // ignore
            } catch (Throwable throwable) {
                priority.remove(peer);
                LogUtils.debug(TAG, "Priority Peer " + peer.toBase58() + " " +
                        throwable.getClass().getName());
            } finally {
                LogUtils.debug(TAG, "Priority Peer " + success + " " +
                        peer.toBase58() + " took " + (System.currentTimeMillis() - start));
            }
        }).start();
    }


    public Block runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {

        matches.put(cid, new ConcurrentLinkedDeque<>());

        long enter = System.currentTimeMillis();

        Set<PeerId> haves = new HashSet<>();
        Set<PeerId> wants = new HashSet<>();
        priority.addAll(host.getPeers());
        whitelist.addAll(host.getRelays());

        while (matches.containsKey(cid)) {

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            for (PeerId peer : priority) {
                if (!haves.contains(peer)) {
                    haves.add(peer);
                    runHaveMessage(closeable, peer, Collections.singletonList(cid));
                }
            }

            ConcurrentLinkedDeque<PeerId> set = matches.get(cid);
            if (set != null) {
                PeerId peer = set.poll();
                if (peer != null) {
                    if (!wants.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            if (matches.containsKey(cid)) {
                                sendWantsMessage(closeable, peer, Collections.singletonList(cid));
                                whitelist.add(peer);
                                wants.add(peer);
                                haves.add(peer);
                                blocker.subscribe(cid, closeable);
                            }
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolIssue issue) {
                            LogUtils.debug(TAG, peer.toBase58() + " " + issue);
                        } catch (Throwable throwable) {
                            whitelist.remove(peer);
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.debug(TAG, "Match Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                        }
                    }
                }
            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            if (System.currentTimeMillis()
                    > (enter + IPFS.BITSWAP_LOAD_PROVIDERS_REFRESH)) {
                loadProviders(closeable, cid);
                enter = System.currentTimeMillis();
            }

        }
        return blockStore.getBlock(cid);
    }


    public void blockReceived(@NonNull PeerId peer, @NonNull Block block) {

        try {
            Cid cid = block.getCid();
            LogUtils.info(TAG, "Block Received " + cid.String() + " " + peer.toBase58());
            blockStore.putBlock(block);

            if (matches.containsKey(cid)) {
                priority.add(peer);
            }

            matches.remove(cid);
            blocker.release(cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean gatePeer(PeerId peerID) {
        return !whitelist.contains(peerID);
    }


    public void loadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {

        LogUtils.verbose(TAG, "LoadBlocks " + cids.size());

        List<PeerId> handled = new ArrayList<>();

        for (PeerId peer : priority) {
            if (!handled.contains(peer)) {
                handled.add(peer);
                runHaveMessage(closeable, peer, cids);
            }
        }
    }

    public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
        try {
            synchronized (cid.String().intern()) {
                Block block = blockStore.getBlock(cid);
                if (block == null) {
                    AtomicBoolean done = new AtomicBoolean(false);
                    LogUtils.info(TAG, "Block Get " + cid.String());

                    if (root) {
                        loadProviders(() -> closeable.isClosed() || done.get(), cid);
                    }
                    try {
                        return runWantHaves(() -> closeable.isClosed() || done.get(), cid);
                    } finally {
                        done.set(true);
                    }
                }
                return block;
            }
        } finally {
            blocker.release(cid);
            LogUtils.info(TAG, "Block Release  " + cid.String());
        }
    }

    private void loadProviders(@NonNull Closeable closeable, @NonNull Cid cid) {

        if (loads.contains(cid)) {
            return;
        }
        loads.add(cid);
        LogUtils.debug(TAG, "Load Provider Start " + cid.String());
        providers.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                if (closeable.isClosed()) {
                    return;
                }
                host.findProviders(closeable, priority::add, cid);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable.getMessage());
            } finally {
                LogUtils.info(TAG, "Load Provider Finish " + cid.String() +
                        " onStart [" + (System.currentTimeMillis() - start) + "]...");
                loads.remove(cid);
            }
        });
    }


    private void sendHaveMessage(@NonNull Closeable closeable,
                                 @NonNull PeerId peer,
                                 @NonNull List<Cid> wantHaves)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {
        if (wantHaves.size() == 0) {
            return;
        }

        int priority = Integer.MAX_VALUE;

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
        bitSwap.writeMessage(closeable, peer, message, IPFS.PRIORITY_URGENT);


    }

    private void sendWantsMessage(@NonNull Closeable closeable,
                                  @NonNull PeerId peer,
                                  @NonNull List<Cid> wantBlocks)
            throws ClosedException, ProtocolIssue, TimeoutIssue, ConnectionIssue {

        if (wantBlocks.size() == 0) {
            return;
        }
        BitSwapMessage message = BitSwapMessage.New(false);

        int priority = Integer.MAX_VALUE;

        for (Cid c : wantBlocks) {

            message.AddEntry(c, priority,
                    MessageOuterClass.Message.Wantlist.WantType.Block, true);

            priority--;
        }

        if (message.Empty()) {
            return;
        }

        LogUtils.debug(TAG, "send WANT Message " + peer.toBase58());
        bitSwap.writeMessage(closeable, peer, message, IPFS.PRIORITY_URGENT);

    }
}
