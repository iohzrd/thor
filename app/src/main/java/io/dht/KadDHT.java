package io.dht;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

import io.Closeable;
import io.LogUtils;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;
import io.ipfs.datastore.MapDataStore;
import io.ipfs.multihash.Multihash;
import io.libp2p.host.Host;
import io.libp2p.peer.AddrInfo;
import io.libp2p.peer.PeerID;
import io.libp2p.protocol.Protocol;
import io.libp2p.routing.Providers;

public class KadDHT implements Routing {
    public static final Protocol Protocol = new Protocol("/ipfs/kad/1.0.0");
    private static final String TAG = KadDHT.class.getSimpleName();
    private final Host host;
    private final ProviderManager providerManager;
    private boolean enableProviders = true;
    public KadDHT(@NonNull Host host){
        this.host = host;
        this.providerManager = new ProviderManager(new MapDataStore());
    }



    @Override
    public void PutValue(@NonNull Closeable closable, String key, byte[] data) {

        LogUtils.error(TAG, key);
    }

    @Override
    public void FindProvidersAsync(@NonNull Providers providers, @NonNull Cid cid, int count) throws ClosedException {
        if(!enableProviders || !cid.Defined()) {
            return;
        }

        int chSize = count;
        if(count == 0) {
            chSize = 1;
        }

        Multihash keyMH = cid.Hash();


        findProvidersAsyncRoutine(providers, keyMH, count);
    }

    private void findProvidersAsyncRoutine(@NonNull Providers providers, @NonNull Multihash key, int count) {


        boolean findAll = count == 0;
        Set<PeerID> ps;
        if(findAll) {
            ps = new HashSet<>(0);
        } else {
            ps = new HashSet<>(10);
        }
        //providerManager.GetProviders(providers, key);
        /* TODO
        provs := providerManager.GetProviders(providers, key);
        for _, p := range provs {
            // NOTE: Assuming that this list of peers is unique
            if ps.TryAdd(p) {
                pi := dht.peerstore.PeerInfo(p)
                select {
                    case peerOut <- pi:
                    case <-ctx.Done():
                        return
                }
            }

            // If we have enough peers locally, don't bother with remote RPC
            // TODO: is this a DOS vector?
            if !findAll && ps.Size() >= count {
                return
            }
        }

        lookupRes, err := dht.runLookupWithFollowup(ctx, string(key),
                func(ctx context.Context, p peer.ID) ([]*peer.AddrInfo, error) {
            // For DHT query command
            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                Type: routing.SendingQuery,
                        ID:   p,
            })

            pmes, err := dht.findProvidersSingle(ctx, p, key)
            if err != nil {
                return nil, err
            }

            logger.Debugf("%d provider entries", len(pmes.GetProviderPeers()))
            provs := pb.PBPeersToPeerInfos(pmes.GetProviderPeers())
            logger.Debugf("%d provider entries decoded", len(provs))

            // Add unique providers from request, up to 'count'
            for _, prov := range provs {
                dht.maybeAddAddrs(prov.ID, prov.Addrs, peerstore.TempAddrTTL)
                logger.Debugf("got provider: %s", prov)
                if ps.TryAdd(prov.ID) {
                    logger.Debugf("using provider: %s", prov)
                    select {
                        case peerOut <- *prov:
                        case <-ctx.Done():
                            logger.Debug("context timed out sending more providers")
                            return nil, ctx.Err()
                    }
                }
                if !findAll && ps.Size() >= count {
                    logger.Debugf("got enough providers (%d/%d)", ps.Size(), count)
                    return nil, nil
                }
            }

            // Give closer peers back to the query to be queried
            closer := pmes.GetCloserPeers()
            peers := pb.PBPeersToPeerInfos(closer)
            logger.Debugf("got closer peers: %d %s", len(peers), peers)

            routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                Type:      routing.PeerResponse,
                        ID:        p,
                        Responses: peers,
            })

            return peers, nil
        },
        func() bool {
            return !findAll && ps.Size() >= count
        },
	)

        if err == nil && ctx.Err() == nil {
            dht.refreshRTIfNoShortcut(kb.ConvertKey(string(key)), lookupRes)
        }*/
    }

    @Override
    public void Provide(@NonNull Closeable closeable, @NonNull Cid cid) {

    }

    @Override
    public AddrInfo FindPeer(@NonNull Closeable closeable, @NonNull PeerID id) {

        /*


            //logger.Debugw("finding peer", "peer", id)

            // Check if were already connected to them
            if pi := dht.FindLocal(id); pi.ID != "" {
                return pi, nil
            }

            lookupRes, err := dht.runLookupWithFollowup(ctx, string(id),
                    func(ctx context.Context, p peer.ID) ([]*peer.AddrInfo, error) {
                // For DHT query command
                routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                    Type: routing.SendingQuery,
                            ID:   p,
                })

                pmes, err := dht.findPeerSingle(ctx, p, id)
                if err != nil {
                    logger.Debugf("error getting closer peers: %s", err)
                    return nil, err
                }
                peers := pb.PBPeersToPeerInfos(pmes.GetCloserPeers())

                // For DHT query command
                routing.PublishQueryEvent(ctx, &routing.QueryEvent{
                    Type:      routing.PeerResponse,
                            ID:        p,
                            Responses: peers,
                })

                return peers, err
            },
            func() bool {
                return dht.host.Network().Connectedness(id) == network.Connected
            },
	)

            if err != nil {
                return peer.AddrInfo{}, err
            }

            dialedPeerDuringQuery := false
            for i, p := range lookupRes.peers {
                if p == id {
                    // Note: we consider PeerUnreachable to be a valid state because the peer may not support the DHT protocol
                    // and therefore the peer would fail the query. The fact that a peer that is returned can be a non-DHT
                    // server peer and is not identified as such is a bug.
                    dialedPeerDuringQuery = (lookupRes.state[i] == qpeerset.PeerQueried || lookupRes.state[i] == qpeerset.PeerUnreachable || lookupRes.state[i] == qpeerset.PeerWaiting)
                    break
                }
            }

            // Return peer information if we tried to dial the peer during the query or we are (or recently were) connected
            // to the peer.
            connectedness := dht.host.Network().Connectedness(id)
            if dialedPeerDuringQuery || connectedness == network.Connected || connectedness == network.CanConnect {
                return dht.peerstore.PeerInfo(id), nil
            }

            return peer.AddrInfo{}, routing.ErrNotFound
      */
        return null;
    }
}
