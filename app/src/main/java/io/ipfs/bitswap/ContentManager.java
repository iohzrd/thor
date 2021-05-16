package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import io.core.Closeable;
import io.core.ClosedException;
import io.core.ConnectionIssue;
import io.core.ProtocolIssue;
import io.core.TimeoutCloseable;
import io.core.TimeoutIssue;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.ipfs.host.PeerId;


public class ContentManager {

    private static final String TAG = ContentManager.class.getSimpleName();

    private final BitSwapNetwork network;
    private final BlockStore blockStore;
    private final ConcurrentSkipListSet<PeerId> faulty = new ConcurrentSkipListSet<>(
            (o1, o2) -> o1.toHex().compareTo(o2.toHex()));
    private final ConcurrentSkipListSet<PeerId> peers = new ConcurrentSkipListSet<>(
            (o1, o2) -> o1.toHex().compareTo(o2.toHex()));
    private final ConcurrentLinkedDeque<PeerId> priority = new ConcurrentLinkedDeque<>();

    private final ConcurrentSkipListSet<Cid> loads = new ConcurrentSkipListSet<>();
    private final ConcurrentHashMap<Cid, ConcurrentLinkedDeque<PeerId>> matches = new ConcurrentHashMap<>();
    private final Blocker blocker = new Blocker();

    public ContentManager(@NonNull BlockStore blockStore, @NonNull BitSwapNetwork network) {
        this.blockStore = blockStore;
        this.network = network;
    }


    public void HaveReceived(@NonNull PeerId peer, @NonNull List<Cid> cids) {

        for (Cid cid : cids) {

            ConcurrentLinkedDeque<PeerId> res = matches.get(cid);
            if (res != null) {
                res.add(peer);

                LogUtils.info(TAG, "HaveReceived " + cid.String() + " " + peer.toBase58());

                if (!Objects.equals(peer, priority.peek())) {
                    priority.push(peer); // top
                }
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
            peers.clear();
            matches.clear();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public Block runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {


        new Thread(() -> {
            long begin = System.currentTimeMillis();
            try {
                network.findProviders(closeable, peer -> {

                    if (closeable.isClosed()) {
                        return;
                    }
                    if (!faulty.contains(peer)) {
                        if (matches.containsKey(cid)) { // check still valid
                            new Thread(() -> {

                                if (matches.containsKey(cid)) { // check still valid
                                    long start = System.currentTimeMillis();
                                    try {

                                        if (closeable.isClosed()) {
                                            return;
                                        }

                                        LogUtils.info(TAG, "Provider Peer " +
                                                peer.toBase58() + " cid " + cid.String());

                                        network.connectTo(closeable, peer, IPFS.CONNECT_TIMEOUT);
                                        if (matches.containsKey(cid)) { // check still valid
                                            LogUtils.info(TAG, "Found New Provider " + peer.toBase58()
                                                    + " for " + cid.String());
                                            peers.add(peer);
                                            ConcurrentLinkedDeque<PeerId> match = matches.get(cid);
                                            if (match != null) {
                                                match.add(peer);
                                            }
                                        }
                                    } catch (ClosedException | ConnectionIssue ignore) {
                                        // ignore
                                    } catch (Throwable throwable) {
                                        LogUtils.error(TAG, throwable);
                                    } finally {
                                        LogUtils.info(TAG, "Provider Peer " +
                                                peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                                    }
                                }
                            }).start();
                        }
                    }
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

        Set<PeerId> handled = new HashSet<>();
        Set<PeerId> wants = new HashSet<>();
        while (matches.containsKey(cid)) {

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            boolean hasRun = false;
            ConcurrentLinkedDeque<PeerId> set = matches.get(cid);
            if (set != null) {
                PeerId peer = set.poll();
                if (peer != null) {
                    if (!wants.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            if (matches.containsKey(cid)) {
                                MessageWriter.sendWantsMessage(closeable, network, peer,
                                        Collections.singletonList(cid));
                                wants.add(peer);
                                handled.add(peer);
                                hasRun = true;
                                blocker.Subscribe(cid, closeable);
                            }
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolIssue ignore) {
                            faulty.add(peer);
                        } catch (Throwable throwable) {
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

            if (!hasRun) {
                for (PeerId peer : priority) {

                    if (!faulty.contains(peer) && !handled.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            peers.add(peer);

                            MessageWriter.sendHaveMessage(closeable, network, peer,
                                    Collections.singletonList(cid));
                            handled.add(peer);
                            hasRun = true;
                        } catch (ClosedException | TimeoutIssue ignore) {
                            // ignore
                        } catch (ProtocolIssue ignore) {
                            peers.remove(peer);
                            priority.remove(peer);
                            faulty.add(peer);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.info(TAG, "Priority Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                        }
                        break;
                    }
                }

            }
            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            if (!hasRun) {
                Set<PeerId> cons = network.getPeers(); // TODO important rethink
                for (PeerId peer : cons) {

                    if (!faulty.contains(peer) && !handled.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            peers.add(peer);
                            MessageWriter.sendHaveMessage(
                                    new TimeoutCloseable(closeable, 10), network, peer,
                                    Collections.singletonList(cid));
                            handled.add(peer);
                        } catch (ClosedException | TimeoutIssue ignore) {
                            // ignore
                        } catch (ProtocolIssue | ConnectionIssue ignore) {
                            peers.remove(peer);
                            priority.remove(peer);
                            faulty.add(peer);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.info(TAG, "Network Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start) +
                                    "  for cid " + cid.String());
                        }

                        // check priority after each run
                        break;

                    }
                }
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
                priority.push(peer);
            }

            matches.remove(cid);
            blocker.Release(cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean GatePeer(PeerId peerID) {
        return !peers.contains(peerID);
    }


    public void LoadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {

        LogUtils.verbose(TAG, "LoadBlocks " + cids.size());

        Executors.newSingleThreadExecutor().execute(() -> {

            List<PeerId> handled = new ArrayList<>();
            boolean wantsMessage = true;
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
                    long start = System.currentTimeMillis();
                    try {
                        if (wantsMessage) {
                            MessageWriter.sendWantsMessage(closeable, network, peer, loads);
                            wantsMessage = false;
                        } else {
                            MessageWriter.sendHaveMessage(closeable, network, peer, loads);
                        }
                    } catch (ClosedException | TimeoutIssue ignore) {
                        // ignore
                    } catch (ProtocolIssue ignore) {
                        faulty.add(peer);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, "LoadBlocks Error " + throwable.getLocalizedMessage());
                    } finally {
                        LogUtils.info(TAG, "LoadBlocks " +
                                peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                    }
                }
            }
        });
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
                    Closeable loadCloseable = new TimeoutCloseable(closeable, 15);

                    network.findProviders(loadCloseable, peer -> {

                        try {
                            LogUtils.info(TAG, "Load Provider " + peer.toBase58() + " for " + cid.String());
                            new Thread(() -> {

                                if (loadCloseable.isClosed()) {
                                    return;
                                }
                                try {
                                    network.connectTo(loadCloseable, peer, IPFS.CONNECT_TIMEOUT);
                                    LogUtils.info(TAG, "Load Provider Found " + peer.toBase58()
                                            + " for " + cid.String());
                                    peers.add(peer);
                                    priority.add(peer);
                                } catch (ClosedException | ConnectionIssue ignore) {
                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, "Load Provider Failed " +
                                            throwable.getLocalizedMessage());
                                }
                            }).start();
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                    }, cid);


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
