package threads.thor.bt.kad.tasks;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.bt.kad.AnnounceNodeCache;
import threads.thor.bt.kad.DBItem;
import threads.thor.bt.kad.DHT.DHTtype;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.KClosestNodesSearch;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.NodeList;
import threads.thor.bt.kad.PeerAddressDBItem;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.messages.GetPeersRequest;
import threads.thor.bt.kad.messages.GetPeersResponse;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.MessageBase.Method;

import static java.lang.Math.min;

/**
 * @author Damokles
 */
public class PeerLookupTask extends IteratingTask {

    private final AnnounceNodeCache cache;
    // nodes which have answered with tokens
    private final Map<KBucketEntry, byte[]> announceCanidates;
    private final Set<PeerAddressDBItem> returnedItems;
    private final boolean useCache = true;

    private BiConsumer<KBucketEntry, PeerAddressDBItem> resultHandler = (x, y) -> {
    };


    public PeerLookupTask(RPCServer rpc, Node node,
                          Key info_hash) {
        super(info_hash, rpc, node);
        announceCanidates = new ConcurrentHashMap<>();
        returnedItems = Collections.newSetFromMap(new ConcurrentHashMap<>());

        cache = rpc.getDHT().getCache();
        // register key even before the task is started so the cache can already accumulate entries
        cache.register(targetKey, false);

        addListener(t -> todo.next());

    }


    public void setResultHandler(BiConsumer<KBucketEntry, PeerAddressDBItem> handler) {
        resultHandler = handler;
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#callFinished(threads.thor.bt.kad.RPCCall, threads.thor.bt.kad.messages.MessageBase)
     */
    @Override
    void callFinished(RPCCall c, MessageBase rsp) {
        if (c.getMessageMethod() != Method.GET_PEERS) {
            return;
        }

        GetPeersResponse gpr = (GetPeersResponse) rsp;

        KBucketEntry match = todo.acceptResponse(c);

        if (match == null)
            return;

        Set<KBucketEntry> returnedNodes = new HashSet<>();

        NodeList nodes = gpr.getNodes(rpc.getDHT().getType());

        if (nodes != null) {
            nodes.entries().filter(e -> !node.isLocalId(e.getID())).forEach(returnedNodes::add);
        }

        todo.addCandidates(match, returnedNodes);

        List<DBItem> items = gpr.getPeerItems();

        for (DBItem item : items) {
            if (!(item instanceof PeerAddressDBItem))
                continue;
            PeerAddressDBItem it = (PeerAddressDBItem) item;
            // also add the items to the returned_items list

            resultHandler.accept(match, it);
            returnedItems.add(it);


        }

        if (returnedItems.size() > 0 && firstResultTime == 0)
            firstResultTime = System.currentTimeMillis();


        // add the peer who responded to the closest nodes list, so we can do an announce
        if (gpr.getToken() != null)
            announceCanidates.put(match, gpr.getToken());


        // if we scrape we don't care about tokens.
        // otherwise we're only done if we have found the closest nodes that also returned tokens
        if (gpr.getToken() != null) {
            closest.insert(match);
        }
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#callTimeout(threads.thor.bt.kad.RPCCall)
     */
    @Override
    void callTimeout(RPCCall c) {
    }

    @Override
    void update() {
        // check if the cache has any closer nodes after the initial query
        if (useCache) {
            Collection<KBucketEntry> cacheResults = cache.get(targetKey,
                    Settings.MAX_CONCURRENT_REQUESTS);
            todo.addCandidates(null, cacheResults);
        }

        for (; ; ) {
            synchronized (this) {
                RequestPermit p = checkFreeSlot();

                if (p == RequestPermit.NONE_ALLOWED)
                    break;

                KBucketEntry e = todo.next2(kbe -> {
                    RequestCandidateEvaluator eval = new RequestCandidateEvaluator(this, closest, todo, kbe, inFlight);
                    return eval.goodForRequest(p);
                }).orElse(null);

                if (e == null)
                    break;

                GetPeersRequest gpr = new GetPeersRequest(targetKey);
                // we only request cross-seeding on find-node
                gpr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT);
                gpr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT);
                gpr.setDestination(e.getAddress());
                gpr.setNoSeeds(false); // TODO check

                if (!rpcCall(gpr, e.getID(), call -> {
                    if (useCache)
                        call.addListener(cache.getRPCListener());
                    call.builtFromEntry(e);

                    long rtt = e.getRTT();
                    long defaultTimeout = rpc.getTimeoutFilter().getStallTimeout();

                    if (rtt < Settings.RPC_CALL_TIMEOUT_MAX) {
                        // the measured RTT is a mean and not the 90th percentile unlike the RPCServer's timeout filter
                        // -> add some safety margin to account for variance
                        rtt = (long) (rtt * (rtt < defaultTimeout ? 2 : 1.5));

                        call.setExpectedRTT(min(rtt, Settings.RPC_CALL_TIMEOUT_MAX));
                    }


                    if (LogUtils.isDebug()) {
                        List<InetSocketAddress> sources = todo.getSources(e).stream().
                                map(KBucketEntry::getAddress).collect(Collectors.toList());
                        LogUtils.verbose(TAG, "Task " + getTaskID() +
                                " sending call to " + e + " sources:" + sources);
                    }

                    todo.addCall(call, e);
                })) {
                    break;
                }
            }
        }

    }


    @Override
    protected boolean isDone() {
        int waitingFor = getNumOutstandingRequests();

        if (waitingFor > 0)
            return false;

        KBucketEntry closest = todo.next().orElse(null);

        if (closest == null) {
            return true;
        }


        RequestCandidateEvaluator eval = new RequestCandidateEvaluator(this, this.closest, todo, closest, inFlight);

        return eval.terminationPrecondition();
    }


    public Map<KBucketEntry, byte[]> getAnnounceCanidates() {
        return announceCanidates;
    }


    /**
     * @return the info_hash
     */
    public Key getInfoHash() {
        return targetKey;
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#start()
     */
    @Override
    public void start() {
        //delay the filling of the todo list until we actually start the task
        KClosestNodesSearch kns = new KClosestNodesSearch(targetKey, Settings.MAX_ENTRIES_PER_BUCKET * 4, rpc.getDHT());
        // unlike NodeLookups we do not use unverified nodes here. this avoids rewarding spoofers with useful lookup target IDs
        kns.fill();
        todo.addCandidates(null, kns.getEntries());

        if (useCache) {
            // re-register once we actually started
            cache.register(targetKey, false);
            todo.addCandidates(null, cache.get(targetKey, Settings.MAX_CONCURRENT_REQUESTS * 2));
        }

        addListener(unused -> logClosest());

        super.start();
    }
}
