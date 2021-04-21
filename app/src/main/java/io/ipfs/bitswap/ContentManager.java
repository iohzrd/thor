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
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.core.PeerId;


public class ContentManager {

    private static final int TIMEOUT = 15000;
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

                LogUtils.error(TAG, "HaveReceived " + cid.String() + " " + peer.toBase58());

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

        LogUtils.error(TAG, "Reset");
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
                network.FindProviders(closeable, peer -> {

                    if (!faulty.contains(peer)) {
                        if (matches.containsKey(cid)) { // check still valid
                            new Thread(() -> {
                                if (matches.containsKey(cid)) { // check still valid
                                    long start = System.currentTimeMillis();
                                    try {
                                        LogUtils.error(TAG, "Provider Peer " +
                                                peer.toBase58() + " cid " + cid.String());

                                        if (network.ConnectTo(closeable, peer)) {
                                            if (matches.containsKey(cid)) { // check still valid
                                                LogUtils.error(TAG, "Found New Provider " + peer.toBase58()
                                                        + " for " + cid.String());
                                                peers.add(peer);
                                                Objects.requireNonNull(matches.get(cid)).add(peer);
                                            }
                                        } else {
                                            LogUtils.error(TAG, "Provider Peer Connection Failed " +
                                                    peer.toBase58());
                                        }
                                    } catch (ClosedException | ConnectionIssue ignore) {
                                        // ignore
                                    } catch (Throwable throwable) {
                                        LogUtils.error(TAG, throwable);
                                    } finally {
                                        LogUtils.error(TAG, "Provider Peer " +
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
                LogUtils.error(TAG, "Finish Provider Search " + cid.String() +
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
                            LogUtils.error(TAG, "Match Peer " +
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
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolIssue ignore) {
                            peers.remove(peer);
                            priority.remove(peer);
                            faulty.add(peer);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.error(TAG, "Priority Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                        }
                        // check priority after each run
                        break;
                    }
                }

            }
            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            if (!hasRun) {
                Set<PeerId> cons = Collections.emptySet();//network.getPeers(); // TODO important rethink
                for (PeerId peer : cons) {

                    if (!faulty.contains(peer) && !handled.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            peers.add(peer);
                            MessageWriter.sendHaveMessage(() -> closeable.isClosed()
                                            || ((System.currentTimeMillis() - start) > 1000), network, peer,
                                    Collections.singletonList(cid));
                            handled.add(peer);
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolIssue | ConnectionIssue ignore) {
                            peers.remove(peer);
                            priority.remove(peer);
                            faulty.add(peer);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.error(TAG, "Network Peer " +
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

        LogUtils.error(TAG, "LoadBlocks " + cids.size());

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
                    LogUtils.error(TAG, "LoadBlocks " + peer.toBase58());
                    long start = System.currentTimeMillis();
                    try {
                        if (wantsMessage) {
                            MessageWriter.sendWantsMessage(closeable, network, peer, loads);
                            wantsMessage = false;
                        } else {
                            MessageWriter.sendHaveMessage(closeable, network, peer, loads);
                        }
                    } catch (ClosedException ignore) {
                        // ignore
                    } catch (ProtocolIssue ignore) {
                        faulty.add(peer);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, "LoadBlocks Error " + throwable.getLocalizedMessage());
                    } finally {
                        LogUtils.error(TAG, "LoadBlocks " +
                                peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                    }
                }
            }
        });
    }

    public void Load(@NonNull Closeable closeable, @NonNull Cid cid) {
        if (!loads.contains(cid)) {
            loads.add(cid);
            LogUtils.error(TAG, "Load Provider Start " + cid.String());
            new Thread(() -> {
                long start = System.currentTimeMillis();
                try {
                    Closeable loadCloseable = () -> closeable.isClosed() || (System.currentTimeMillis() - start) > TIMEOUT;

                    network.FindProviders(loadCloseable, peer -> {

                        try {
                            LogUtils.error(TAG, "Load Provider " + peer.toBase58() + " for " + cid.String());
                            new Thread(() -> {
                                try {
                                    if (network.ConnectTo(loadCloseable, peer)) {
                                        LogUtils.error(TAG, "Load Provider Found " + peer.toBase58()
                                                + " for " + cid.String());
                                        peers.add(peer);
                                        priority.add(peer);
                                    } else {
                                        LogUtils.error(TAG, "Load Provider Connection Failed " +
                                                peer.toBase58());
                                    }
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
