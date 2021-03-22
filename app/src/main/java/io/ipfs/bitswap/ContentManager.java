package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;
import io.libp2p.peer.PeerID;
import io.libp2p.routing.Providers;

public class ContentManager {
    public static final int PROVIDERS = 10;
    private static final int TIMEOUT = 10000;
    private static final String TAG = ContentManager.class.getSimpleName();
    private static final ExecutorService WANTS = Executors.newFixedThreadPool(8);
    private static final ExecutorService HAVES = Executors.newFixedThreadPool(2);
    private final ConcurrentSkipListSet<Cid> searches = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerID> faulty = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerID> peers = new ConcurrentSkipListSet<>();
    private final ConcurrentLinkedDeque<PeerID> priority = new ConcurrentLinkedDeque<>();
    private final BitSwapNetwork network;
    private final BlockStore blockStore;
    private final Pubsub notify = new Pubsub();

    public ContentManager(@NonNull BlockStore blockStore,
                          @NonNull BitSwapNetwork network) {
        this.blockStore = blockStore;
        this.network = network;
    }

    public void HaveReceived(@NonNull PeerID peer, @NonNull List<Cid> cids) {
        for (Cid cid : cids) {
            if (searches.contains(cid)) {
                LogUtils.info(TAG, "HaveReceived " + cid.String());
                faulty.remove(peer);
                priority.remove(peer);
                priority.push(peer); // top
            }
        }
    }

    public void reset() {
        try {
            searches.clear();
            priority.clear();
            notify.clear();
            peers.clear();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void runWantHaves(@NonNull Closeable closeable, @NonNull Cid cid) throws ClosedException {

        // TODO event for new connections add them to peers

        CopyOnWriteArraySet<PeerID> wants = new CopyOnWriteArraySet<>();
        Set<PeerID> haves = new HashSet<>(); // ok, does not changed by threads
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                network.FindProvidersAsync(new Providers() {
                    @Override
                    public void Peer(@NonNull String pid) {
                        PeerID peer = new PeerID(pid);

                        if (!wants.contains(peer)) {
                            wants.add(peer);
                            WANTS.execute(() -> {
                                if (searches.contains(cid)) { // check still valid
                                    long start = System.currentTimeMillis();
                                    try {

                                        if (network.ConnectTo(() -> closeable.isClosed()
                                                        || ((System.currentTimeMillis() - start) > TIMEOUT),
                                                peer, true)) {
                                            if (searches.contains(cid)) { // check still valid
                                                LogUtils.verbose(TAG, "Found New Provider " + pid
                                                        + " for " + cid.String());
                                                peers.add(peer);
                                                MessageWriter.sendWantsMessage(closeable, network, peer,
                                                        Collections.singletonList(cid));
                                            }
                                        }
                                    } catch (ClosedException ignore) {
                                        // ignore
                                    } catch (Throwable throwable) {
                                        priority.remove(peer);
                                        faulty.add(peer);
                                        //LogUtils.error(TAG, throwable);
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
            }
        });


        boolean hasRun;
        do {
            hasRun = false;

            if (closeable.isClosed()) {
                throw new ClosedException();
            }

            for (PeerID peer : priority) {
                if (!wants.contains(peer)) {
                    wants.add(peer);
                    hasRun = true;
                    long start = System.currentTimeMillis();
                    try {
                        if (searches.contains(cid)) { // check still valid
                            peers.add(peer);
                            MessageWriter.sendWantsMessage(closeable, network, peer,
                                    Collections.singletonList(cid));
                        }
                    } catch (ClosedException closedException) {
                        // ignore
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                        priority.remove(peer);
                        faulty.add(peer);
                        peers.remove(peer);
                    } finally {
                        LogUtils.error(TAG, "Priority Peer " +
                                peer.String() + " took " + (System.currentTimeMillis() - start));
                    }
                    // check priority after each run
                    break;
                }
            }

            if (closeable.isClosed()) {
                throw new ClosedException();
            }


            if (!hasRun) {
                List<PeerID> cons = network.getPeers();
                for (PeerID peer : cons) {
                    if (!faulty.contains(peer) && !wants.contains(peer)
                            && !haves.contains(peer) && !priority.contains(peer)) {
                        haves.add(peer);

                        HAVES.execute(() -> {
                            long start = System.currentTimeMillis();
                            try {
                                peers.add(peer);
                                MessageWriter.sendHaveMessage(closeable, network, peer,
                                        Collections.singletonList(cid));

                            } catch (ClosedException closedException) {
                                // ignore
                            } catch (Throwable throwable) {
                                //LogUtils.error(TAG, throwable);
                                peers.remove(peer);
                                faulty.add(peer);
                            } finally {
                                LogUtils.error(TAG, "Network Peer " +
                                        peer.String() + " took " + (System.currentTimeMillis() - start));
                            }
                        });
                        // check priority after each run
                        break;

                    }
                }
            }

        } while (searches.contains(cid));

    }

    public Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) {

        try {
            if (!searches.contains(cid)) {
                LogUtils.info(TAG, "BlockGet " + cid.String());
                searches.add(cid);
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        runWantHaves(closeable, cid);
                    } catch (ClosedException closedException) {
                        LogUtils.error(TAG, "BlockClosed " + cid.String());
                        notify.Release(cid);
                    }
                });
            }
            return notify.Subscribe(cid);
        } finally {
            LogUtils.info(TAG, "BlockRelease  " + cid.String());
            searches.remove(cid);
        }

    }

    public void BlockReceived(@NonNull PeerID peer, @NonNull Block block) {

        try {
            Cid cid = block.Cid();
            LogUtils.info(TAG, "BlockReceived " + cid.String());
            blockStore.Put(block);
            if (searches.contains(cid)) {
                faulty.remove(peer);
                priority.remove(peer);
                priority.add(peer);
            }
            notify.Publish(block);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public boolean GatePeer(PeerID peerID) {
        return !peers.contains(peerID);
    }

    public void LoadBlocks(@NonNull Closeable closeable, @NonNull List<Cid> cids) {

        LogUtils.error(TAG, "LoadBlocks " + cids.size());

        for (PeerID peer : priority) {
            LogUtils.error(TAG, "LoadBlock " + peer.String());
            long start = System.currentTimeMillis();
            try {
                MessageWriter.sendWantsMessage(closeable, network, peer, cids);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, "LoadBlock Error " + throwable.getLocalizedMessage());
            } finally {
                LogUtils.error(TAG, "Load Peer " +
                        peer.String() + " took " + (System.currentTimeMillis() - start));
            }
        }
    }
}
