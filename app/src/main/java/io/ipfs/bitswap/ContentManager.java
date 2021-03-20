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
    private static final int TIMEOUT = 5000;
    private static final String TAG = ContentManager.class.getSimpleName();
    private static final ExecutorService WANTS = Executors.newFixedThreadPool(4);
    private static final ExecutorService HAVES = Executors.newFixedThreadPool(4);
    private final ConcurrentSkipListSet<Cid> searches = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<Cid> valids = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PeerID> faulty = new ConcurrentSkipListSet<>();
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
        searches.clear();
        priority.clear();
        notify.clear();
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

                                try {
                                    if (network.ConnectTo(closeable, peer, true)) {
                                        LogUtils.verbose(TAG, "Found New Provider " + pid);
                                        MessageWriter.sendWantsMessage(closeable, network, peer,
                                                Collections.singletonList(cid));
                                    }
                                } catch (ClosedException ignore) {
                                    // ignore
                                } catch (Throwable throwable) {
                                    priority.remove(peer);
                                    faulty.add(peer);
                                    //LogUtils.error(TAG, throwable);
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
                    try {
                        if (network.ConnectTo(closeable, peer, true)) {
                            MessageWriter.sendWantsMessage(closeable, network, peer,
                                    Collections.singletonList(cid));
                        } else {
                            priority.remove(peer);
                        }
                    } catch (ClosedException closedException) {
                        throw closedException;
                    } catch (Throwable throwable) {
                        //LogUtils.error(TAG, throwable);
                        priority.remove(peer);
                        faulty.add(peer);
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
                            try {
                                long start = System.currentTimeMillis();
                                if (network.ConnectTo(() -> closeable.isClosed()
                                        || ((System.currentTimeMillis() - start) > TIMEOUT), peer, false)) {
                                    MessageWriter.sendHaveMessage(closeable, network, peer,
                                            Collections.singletonList(cid));
                                }
                            } catch (ClosedException closedException) {
                                // ignore
                            } catch (Throwable throwable) {
                                //LogUtils.error(TAG, throwable);
                                faulty.add(peer);
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
            LogUtils.info(TAG, "GetBlock Start " + cid.String());
            if (!searches.contains(cid)) {
                searches.add(cid);
                valids.add(cid);
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        runWantHaves(closeable, cid);
                    } catch (ClosedException closedException) {
                        notify.Release(cid);
                    }
                });
            }
            return notify.Subscribe(cid);
        } finally {
            LogUtils.info(TAG, "GetBlock Release " + cid.String());
            searches.remove(cid);
        }

    }

    public void BlockReceived(@NonNull PeerID peer, @NonNull Block block) {

        Cid cid = block.Cid();

        try {
            // TODO maybe the spam filter is not really necessary
            if (valids.contains(cid)) {
                blockStore.Put(block);
            } else {
                LogUtils.error(TAG, "!!! BlockReceived for not asked block (SPAM) !!!");
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        if (searches.contains(cid)) {

            LogUtils.info(TAG, "BlockReceived " + cid.String());

            notify.Publish(block);
            faulty.remove(peer);
            priority.remove(peer);
            priority.add(peer);

        }
    }

}
