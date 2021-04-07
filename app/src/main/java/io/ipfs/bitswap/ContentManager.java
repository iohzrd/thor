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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.Closeable;
import io.LogUtils;
import io.dht.Providers;
import io.ipfs.ClosedException;
import io.ipfs.IPFS;
import io.ipfs.ProtocolNotSupported;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.core.PeerId;


public class ContentManager {
    public static final int PROVIDERS = 10;
    private static final int TIMEOUT = 15000;
    private static final String TAG = ContentManager.class.getSimpleName();
    private final BitSwapNetwork network;
    private final BlockStore blockStore;

    private static final ExecutorService LOADS = Executors.newFixedThreadPool(4);
    private static final ExecutorService WANTS = Executors.newFixedThreadPool(4);

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

            if (matches.containsKey(cid)) {
                LogUtils.error(TAG, "HaveReceived " + cid.String() + " " + peer.toBase58());

                if (!Objects.equals(peer, priority.peek())) {
                    priority.push(peer); // top
                }

                matches.get(cid).add(peer);
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


        Executors.newSingleThreadExecutor().execute(() -> {
            long begin = System.currentTimeMillis();
            try {
                network.FindProvidersAsync(new Providers() {
                    @Override
                    public void Peer(@NonNull String pid) {
                        PeerId peer = PeerId.fromBase58(pid);
                        LogUtils.error(TAG, "Provider Peer Step " + peer.toBase58());
                        if (!faulty.contains(peer)) {
                            WANTS.execute(() -> {
                                if (matches.containsKey(cid)) { // check still valid
                                    long start = System.currentTimeMillis();
                                    try {
                                        LogUtils.error(TAG, "Provider Peer " +
                                                peer.toBase58() + " cid " + cid.String());

                                        if (network.ConnectTo(() -> closeable.isClosed()
                                                        || ((System.currentTimeMillis() - start) > TIMEOUT),
                                                peer, true)) {
                                            if (matches.containsKey(cid)) { // check still valid
                                                LogUtils.error(TAG, "Found New Provider " + pid
                                                        + " for " + cid.String());
                                                peers.add(peer);
                                                matches.get(cid).add(peer);
                                            }
                                        } else {
                                            LogUtils.error(TAG, "Provider Peer Connection Failed " +
                                                    peer.toBase58());
                                        }
                                    } catch (ClosedException ignore) {
                                        // ignore
                                    } catch (Throwable throwable) {
                                        LogUtils.error(TAG, throwable);
                                    } finally {
                                        LogUtils.error(TAG, "Provider Peer " +
                                                peer.toBase58() + " took " + (System.currentTimeMillis() - start));
                                    }
                                }
                            });

                        }
                    }

                    @Override
                    public boolean isClosed() {
                        return closeable.isClosed();
                    }
                }, cid, PROVIDERS);
            } catch (ClosedException closedException) {
                // ignore here
            } finally {
                LogUtils.error(TAG, "Finish Provider Search " + (System.currentTimeMillis() - begin));
            }
        });

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
                                        Collections.singletonList(cid), IPFS.WRITE_TIMEOUT);
                                wants.add(peer);
                                handled.add(peer);
                                hasRun = true;
                                blocker.Subscribe(cid, closeable);
                            }
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolNotSupported ignore) {
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
                                    Collections.singletonList(cid), IPFS.WRITE_TIMEOUT);
                            handled.add(peer);
                            hasRun = true;
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolNotSupported ignore) {
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
                Set<PeerId> cons = network.getPeers();
                for (PeerId peer : cons) {

                    if (!faulty.contains(peer) && !handled.contains(peer)) {
                        long start = System.currentTimeMillis();
                        try {
                            peers.add(peer);
                            MessageWriter.sendHaveMessage(() -> closeable.isClosed()
                                            || ((System.currentTimeMillis() - start) > 1000), network, peer,
                                    Collections.singletonList(cid), 1);
                            handled.add(peer);
                        } catch (ClosedException closedException) {
                            // ignore
                        } catch (ProtocolNotSupported ignore) {
                            peers.remove(peer);
                            priority.remove(peer);
                            faulty.add(peer);
                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        } finally {
                            LogUtils.error(TAG, "Network Peer " +
                                    peer.toBase58() + " took " + (System.currentTimeMillis() - start));
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
                        if(wantsMessage){
                            MessageWriter.sendWantsMessage(closeable, network, peer, loads,
                                    IPFS.WRITE_TIMEOUT);
                            wantsMessage = false;
                        } else {
                            MessageWriter.sendHaveMessage(closeable, network, peer, loads,
                                    IPFS.WRITE_TIMEOUT);
                        }
                    } catch (ClosedException ignore) {
                        // ignore
                    } catch (ProtocolNotSupported ignore) {
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
            Executors.newSingleThreadExecutor().execute(() -> {
                long start = System.currentTimeMillis();
                try {

                    network.FindProvidersAsync(new Providers() {

                        @Override
                        public void Peer(@NonNull String pid) {
                            PeerId peer = PeerId.fromBase58(pid);

                            try {
                                LogUtils.error(TAG, "Load Provider " + pid + " for " + cid.String());

                                LOADS.execute(() -> {
                                    try {
                                        if (network.ConnectTo(() -> closeable.isClosed()
                                                        || ((System.currentTimeMillis() - start) > TIMEOUT),
                                                peer, true)) {
                                            LogUtils.error(TAG, "Load Provider Found " + pid
                                                    + " for " + cid.String());
                                            peers.add(peer);
                                            priority.add(peer);
                                        } else {
                                            LogUtils.error(TAG, "Load Provider Connection Failed " +
                                                    peer.toBase58());
                                        }
                                    } catch (ClosedException ignore) {
                                    } catch (Throwable throwable) {
                                        LogUtils.error(TAG, "Load Provider Failed " +
                                                throwable.getLocalizedMessage());
                                    }
                                });
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                            }
                        }


                        @Override
                        public boolean isClosed() {
                            return closeable.isClosed();
                        }
                    }, cid, PROVIDERS);


                } catch (ClosedException ignore) {
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable.getMessage());
                } finally {
                    LogUtils.info(TAG, "Finish " + cid.String() +
                            " onStart [" + (System.currentTimeMillis() - start) + "]...");
                }
            });
        }
    }
}
