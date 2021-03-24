package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.ProtocolNotSupported;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.peer.PeerID;
import io.libp2p.routing.Providers;

public class ContentManager {
    public static final int PROVIDERS = 10;
    private static final int TIMEOUT = 15000;
    private static final String TAG = ContentManager.class.getSimpleName();
    private static final ExecutorService LOADS = Executors.newFixedThreadPool(4);
    private static final ExecutorService WANTS = Executors.newFixedThreadPool(8);
    private final ConcurrentSkipListSet<Cid> searches = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<String> haves = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerID> faulty = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerID> peers = new ConcurrentSkipListSet<>();
    private final ConcurrentLinkedDeque<PeerID> priority = new ConcurrentLinkedDeque<>();
    private final BitSwapNetwork network;
    private final BlockStore blockStore;
    private final ConcurrentSkipListSet<Cid> loads = new ConcurrentSkipListSet<>();
    private final ConcurrentHashMap<Cid, ConcurrentLinkedDeque<PeerID>> matches = new ConcurrentHashMap<>();


    public ContentManager(@NonNull BlockStore blockStore,
                          @NonNull BitSwapNetwork network) {
        this.blockStore = blockStore;
        this.network = network;
    }


    public void HaveReceived(@NonNull PeerID peer, @NonNull List<Cid> cids) {

        for (Cid cid : cids) {

            if (!searches.contains(cid)) {
                LogUtils.error(TAG, "HaveReceived " + cid.String() + " " + peer.String());

                if (Objects.equals(peer, priority.peek())) {
                    priority.push(peer); // top
                }

                ConcurrentLinkedDeque<PeerID> set = matches.get(cid);
                if(set == null){
                    set = new ConcurrentLinkedDeque<>();
                    matches.put(cid, set);
                }
                set.add(peer);
            }
        }
    }

    public void reset() {

        LogUtils.error(TAG, "Reset");
        try {
            haves.clear();
            searches.clear();
            loads.clear();
            priority.clear();
            peers.clear();
            matches.clear();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public Block runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {


        if(!searches.contains(cid)) {
            Executors.newSingleThreadExecutor().execute(() -> {
                long begin = System.currentTimeMillis();
                try {
                    network.FindProvidersAsync(new Providers() {
                        @Override
                        public void Peer(@NonNull String pid) {
                            PeerID peer = new PeerID(pid);
                            LogUtils.error(TAG, "Provider Peer Step " + peer.String());
                            if (!faulty.contains(peer)) {
                                WANTS.execute(() -> {
                                    if (!searches.contains(cid)) { // check still valid
                                        long start = System.currentTimeMillis();
                                        try {
                                            LogUtils.error(TAG, "Provider Peer " +
                                                    peer.String() + " cid " + cid.String());

                                            if (network.ConnectTo(() -> closeable.isClosed()
                                                            || ((System.currentTimeMillis() - start) > TIMEOUT),
                                                    peer, true)) {
                                                if (!searches.contains(cid)) { // check still valid
                                                    LogUtils.error(TAG, "Found New Provider " + pid
                                                            + " for " + cid.String());
                                                    priority.push(peer);
                                                }
                                            } else {
                                                LogUtils.error(TAG, "Provider Peer Connection Failed " +
                                                        peer.String());
                                            }
                                        } catch (ClosedException ignore) {
                                            // ignore
                                        } catch (Throwable throwable) {
                                            LogUtils.error(TAG, throwable);
                                        } finally {
                                            LogUtils.error(TAG, "Provider Peer " +
                                                    peer.String() + " took " + (System.currentTimeMillis() - start));
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
        }

        while (!searches.contains(cid)) {

            if (closeable.isClosed()) {
                throw new ClosedException();
            }


            boolean hasRun = false;


            ConcurrentLinkedDeque<PeerID> set = matches.get(cid);
            if(set != null) {
                PeerID peer = set.poll();
                if(peer != null) {
                    long start = System.currentTimeMillis();
                    try {
                        MessageWriter.sendWantsMessage(closeable, network, peer,
                                Collections.singletonList(cid));
                        hasRun = true;
                    } catch (ClosedException closedException) {
                        // ignore
                    } catch (ProtocolNotSupported ignore) {
                        faulty.add(peer);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    } finally {
                        LogUtils.error(TAG, "Match Peer " +
                                peer.String() + " took " + (System.currentTimeMillis() - start));
                    }
                }
            }


            if (!hasRun) {
                for (PeerID peer : priority) {
                    String key = getKey(peer, cid);
                    if (!faulty.contains(peer) && !haves.contains(key)) {
                        long start = System.currentTimeMillis();
                        try {
                            peers.add(peer);
                            MessageWriter.sendHaveMessage(closeable, network, peer,
                                    Collections.singletonList(cid));
                            haves.add(key);
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
                                    peer.String() + " took " + (System.currentTimeMillis() - start));
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
                List<PeerID> cons = network.getPeers();
                for (PeerID peer : cons) {
                    String key = getKey(peer, cid);
                    if (!faulty.contains(peer) && !haves.contains(key)) {
                        long start = System.currentTimeMillis();
                        try {
                            peers.add(peer);
                            MessageWriter.sendHaveMessage(() -> closeable.isClosed()
                                            || ((System.currentTimeMillis() - start) > 1000), network, peer,
                                    Collections.singletonList(cid));
                            haves.add(key);
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
                                    peer.String() + " took " + (System.currentTimeMillis() - start));
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
                AtomicBoolean done = new AtomicBoolean(false);
                LogUtils.info(TAG, "BlockGet " + cid.String());
                try {
                    return runWantHaves(() -> closeable.isClosed() || done.get(), cid);
                } finally {
                    done.set(true);
                }
            }
        } finally {
            LogUtils.info(TAG, "BlockRelease  " + cid.String());
        }

    }

    public void BlockReceived(@NonNull PeerID peer, @NonNull Block block) {

        try {
            Cid cid = block.Cid();
            LogUtils.info(TAG, "Block Received " + cid.String() + " " + peer);
            blockStore.Put(block);

            if (!searches.contains(cid)) {
                priority.push(peer);
            }
            searches.add(cid);
            matches.remove(cid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean GatePeer(PeerID peerID) {
        return !peers.contains(peerID);
    }

    public String getKey(@NonNull PeerID peer, @NonNull Cid cid) {
        return peer.String().concat(cid.String());
    }

    public void LoadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {


        LogUtils.error(TAG, "LoadBlocks " + cids.size());

        Executors.newSingleThreadExecutor().execute(() -> {

            for (PeerID peer : priority) {
                List<Cid> writes = new ArrayList<>();
                for (Cid cid : cids) {
                    if (!haves.contains(getKey(peer, cid))) {
                        writes.add(cid);
                    }
                }
                LogUtils.error(TAG, "LoadBlock " + peer.String());
                long start = System.currentTimeMillis();
                try {
                    MessageWriter.sendHaveMessage(closeable, network, peer, writes);
                    for (Cid cid : cids) {
                        haves.add(getKey(peer, cid));
                    }
                } catch (ClosedException ignore) {
                    // ignore
                } catch (ProtocolNotSupported ignore) {
                    faulty.add(peer);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, "LoadBlock Error " + throwable.getLocalizedMessage());
                } finally {
                    LogUtils.error(TAG, "LoadBlock " +
                            peer.String() + " took " + (System.currentTimeMillis() - start));
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
                            PeerID peer = new PeerID(pid);


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
                                                    peer.String());
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
