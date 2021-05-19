package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.core.ConnectionIssue;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.core.TimeoutIssue;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.ipfs.host.PeerId;


public class ContentManager {

    private static final String TAG = ContentManager.class.getSimpleName();

    private final BitSwapNetwork network;
    private final BlockStore blockStore;

    private final ConcurrentSkipListSet<PeerId> whitelist = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerId> priority = new ConcurrentSkipListSet<>();

    private final ConcurrentSkipListSet<Cid> loads = new ConcurrentSkipListSet<>();
    private final ConcurrentHashMap<Cid, ConcurrentLinkedDeque<PeerId>> matches = new ConcurrentHashMap<>();
    private final Blocker blocker = new Blocker();
    private final BitSwap bitSwap;

    public ContentManager(@NonNull BitSwap bitSwap, @NonNull BlockStore blockStore, @NonNull BitSwapNetwork network) {
        this.bitSwap = bitSwap;
        this.blockStore = blockStore;
        this.network = network;
    }


    public void HaveReceived(@NonNull PeerId peer, @NonNull List<Cid> cids) {

        for (Cid cid : cids) {

            ConcurrentLinkedDeque<PeerId> res = matches.get(cid);
            if (res != null) {
                res.add(peer);

                LogUtils.info(TAG, "HaveReceived " + cid.String() + " " + peer.toBase58());

                priority.add(peer);

            }
        }
    }

    private void createMatch(@NonNull Cid cid) {
        if (!matches.containsKey(cid)) {
            matches.put(cid, new ConcurrentLinkedDeque<>());
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

    public void runHaveMessage(@NonNull Closeable closeable, @NonNull PeerId peer, @NonNull List<Cid> cids) {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                MessageWriter.sendHaveMessage(closeable, bitSwap, peer, cids);
                whitelist.add(peer);
            } catch (ClosedException | TimeoutIssue ignore) {
                // ignore
            } catch (ProtocolIssue ignore) {
                priority.remove(peer);
            } catch (Throwable throwable) {
                priority.remove(peer);
                LogUtils.error(TAG, throwable);
            } finally {
                LogUtils.info(TAG, "Priority Peer " +
                        peer.toBase58() + " took " + (System.currentTimeMillis() - start));
            }
        }).start();
    }

    public Block runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {


        new Thread(() -> {
            long begin = System.currentTimeMillis();
            try {
                network.findProviders(closeable, peer -> {

                    if (closeable.isClosed()) {
                        return;
                    }

                    if (!matches.containsKey(cid)) { // check still valid
                        return;
                    }

                    new Thread(() -> {

                        if (!matches.containsKey(cid)) { // check still valid
                            return;
                        }

                        long start = System.currentTimeMillis();
                        try {

                            if (closeable.isClosed()) {
                                return;
                            }

                            LogUtils.info(TAG, "Provider Peer " +
                                    peer.toBase58() + " cid " + cid.String());

                            network.connectTo(closeable, peer, IPFS.CONNECT_TIMEOUT);

                            if (!matches.containsKey(cid)) { // check still valid
                                return;
                            }

                            LogUtils.info(TAG, "Found New Provider " + peer.toBase58()
                                    + " for " + cid.String());

                            ConcurrentLinkedDeque<PeerId> match = matches.get(cid);
                            if (match != null) {
                                match.add(peer);
                            }

                        } catch (ClosedException | ConnectionIssue ignore) {
                            // ignore
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.info(TAG, "Provider Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                        }

                    }).start();


                }, cid);
            } catch (ClosedException closedException) {
                // ignore here
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                LogUtils.info(TAG, "Finish Provider Search " + cid.String() +
                        " " + (System.currentTimeMillis() - begin));
            }
        }).start();

        Set<PeerId> haves = new HashSet<>();
        Set<PeerId> wants = new HashSet<>();
        priority.addAll(network.getPeers());
        while (matches.containsKey(cid)) {

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            ConcurrentLinkedDeque<PeerId> set = matches.get(cid);
            if (set != null) {
                PeerId peer = set.poll();
                if (peer != null) {
                    if (!wants.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            if (matches.containsKey(cid)) {
                                MessageWriter.sendWantsMessage(closeable, bitSwap, peer,
                                        Collections.singletonList(cid));
                                whitelist.add(peer);
                                wants.add(peer);
                                haves.add(peer);
                                blocker.Subscribe(cid, closeable);
                            }
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolIssue ignore) {
                            whitelist.remove(peer);
                        } catch (Throwable throwable) {
                            whitelist.remove(peer);
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.info(TAG, "Match Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                        }
                    }
                }
            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }


            for (PeerId peer : priority) {
                if (!haves.contains(peer)) {
                    haves.add(peer);
                    runHaveMessage(closeable, peer, Collections.singletonList(cid));
                }
            }


            if (closeable.isClosed()) {
                throw new ClosedException();
            }

        }
        return blockStore.Get(cid);
    }

    public Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {
        try {
            synchronized (cid.String().intern()) {
                Block block = blockStore.Get(cid);
                if (block == null) {
                    createMatch(cid);
                    AtomicBoolean done = new AtomicBoolean(false);
                    LogUtils.info(TAG, "Block Get " + cid.String());
                    try {
                        return runWantHaves(() -> closeable.isClosed() || done.get(), cid);
                    } finally {
                        done.set(true);
                    }
                }
                return block;
            }
        } finally {
            blocker.Release(cid);
            LogUtils.info(TAG, "Block Release  " + cid.String());
        }
    }

    public void BlockReceived(@NonNull PeerId peer, @NonNull Block block) {

        try {
            Cid cid = block.Cid();
            LogUtils.info(TAG, "Block Received " + cid.String() + " " + peer.toBase58());
            blockStore.Put(block);

            if (matches.containsKey(cid)) {
                priority.add(peer);
            }

            matches.remove(cid);
            blocker.Release(cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean GatePeer(PeerId peerID) {
        return !whitelist.contains(peerID);
    }


    public void LoadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {

        LogUtils.verbose(TAG, "LoadBlocks " + cids.size());

        List<PeerId> handled = new ArrayList<>();

        for (PeerId peer : priority) {
            if (!handled.contains(peer)) {
                handled.add(peer);
                List<Cid> loads = new ArrayList<>();
                for (Cid cid : cids) {
                    if (!matches.containsKey(cid)) {
                        loads.add(cid);
                        createMatch(cid);
                    }
                }
                LogUtils.verbose(TAG, "LoadBlocks " + peer.toBase58());

                runHaveMessage(closeable, peer, loads);
            }
        }
    }

    public void Load(@NonNull Closeable closeable, @NonNull Cid cid) {
        if (!loads.contains(cid)) {
            loads.add(cid);
            LogUtils.info(TAG, "Load Provider Start " + cid.String());

            new Thread(() -> {
                long start = System.currentTimeMillis();
                try {
                    if (closeable.isClosed()) {
                        return;
                    }
                    network.findProviders(closeable, priority::add, cid);
                } catch (ClosedException ignore) {
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable.getMessage());
                } finally {
                    LogUtils.info(TAG, "Finish " + cid.String() +
                            " onStart [" + (System.currentTimeMillis() - start) + "]...");
                }
            }).start();
        }
    }
}
