package threads.thor.magnet.kad.tasks;


import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.magnet.kad.AddressUtils;
import threads.thor.magnet.kad.DHT.DHTtype;
import threads.thor.magnet.kad.KBucketEntry;
import threads.thor.magnet.kad.KClosestNodesSearch;
import threads.thor.magnet.kad.Key;
import threads.thor.magnet.kad.Node;
import threads.thor.magnet.kad.NodeList;
import threads.thor.magnet.kad.RPCCall;
import threads.thor.magnet.kad.RPCServer;
import threads.thor.magnet.kad.messages.FindNodeRequest;
import threads.thor.magnet.kad.messages.FindNodeResponse;
import threads.thor.magnet.kad.messages.MessageBase;
import threads.thor.magnet.kad.messages.MessageBase.Method;
import threads.thor.magnet.kad.messages.MessageBase.Type;

/**
 * @author Damokles
 */
public class NodeLookup extends IteratingTask {
    private final boolean forBootstrap;


    public NodeLookup(Key node_id, RPCServer rpc, Node node, boolean isBootstrap) {
        super(node_id, rpc, node);
        forBootstrap = isBootstrap;
        addListener(t -> updatedPopulationEstimates());
    }

    @Override
    void update() {
        for (; ; ) {
            // while(!todo.isEmpty() && canDoRequest() && !isClosestSetStable() && !nextTodoUseless())
            RequestPermit p = checkFreeSlot();
            if (p == RequestPermit.NONE_ALLOWED)
                return;

            KBucketEntry e = todo.next().orElse(null);

            if (e == null)
                return;

            if (!new RequestCandidateEvaluator(this, closest, todo, e, inFlight).goodForRequest(p))
                return;

            // send a findNode to the node
            FindNodeRequest fnr = new FindNodeRequest(targetKey);
            fnr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT);
            fnr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT);
            fnr.setDestination(e.getAddress());

            if (!rpcCall(fnr, e.getID(), (call) -> {
                long rtt = e.getRTT();
                rtt = rtt + rtt / 2; // *1.5 since this is the average and not the 90th percentile like the timeout filter
                if (rtt < Settings.RPC_CALL_TIMEOUT_MAX && rtt < rpc.getTimeoutFilter().getStallTimeout())
                    call.setExpectedRTT(rtt); // only set a node-specific timeout if it's better than what the server would apply anyway
                call.builtFromEntry(e);
                todo.addCall(call, e);


                if (LogUtils.isDebug()) {
                    List<InetSocketAddress> sources = todo.getSources(e).stream().
                            map(KBucketEntry::getAddress).collect(Collectors.toList());
                    LogUtils.verbose(TAG, "Task " + getTaskID() +
                            " sending call to " + e + " sources:" + sources);
                }

            })) {
                break;
            }

        }
    }

    @Override
    protected boolean isDone() {
        int waitingFor = getNumOutstandingRequests();

        if (waitingFor != 0)
            return false;

        KBucketEntry next = todo.next().orElse(null);

        if (next == null)
            return true;

        return new RequestCandidateEvaluator(this, closest, todo, next, inFlight).terminationPrecondition();
    }

    @Override
    void callFinished(RPCCall c, MessageBase rsp) {

        // check the response and see if it is a good one
        if (rsp.getMethod() != Method.FIND_NODE || rsp.getType() != Type.RSP_MSG)
            return;

        KBucketEntry match = todo.acceptResponse(c);

        if (match == null)
            return;

        FindNodeResponse fnr = (FindNodeResponse) rsp;

        closest.insert(match);


        for (DHTtype type : DHTtype.values()) {
            NodeList nodes = fnr.getNodes(type);
            if (nodes == null)
                continue;
            if (type == rpc.getDHT().getType()) {
                Set<KBucketEntry> entries = nodes.entries().filter(e ->
                        !AddressUtils.isBogon(e.getAddress()) &&
                                !node.isLocalId(e.getID())).collect(Collectors.toSet());
                todo.addCandidates(match, entries);
            }
        }

    }

    @Override
    void callTimeout(RPCCall c) {

    }

    public void injectCandidates(Collection<KBucketEntry> cand) {
        todo.addCandidates(null, cand);
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#start()
     */
    @Override
    public void start() {

        // if we're bootstrapping start from the bucket that has the greatest possible distance from ourselves so we discover new things along the (longer) path
        Key knsTargetKey = forBootstrap ? targetKey.distance(Key.MAX_KEY) : targetKey;

        // delay the filling of the todo list until we actually start the task
        KClosestNodesSearch kns = new KClosestNodesSearch(knsTargetKey, 3 * Settings.MAX_ENTRIES_PER_BUCKET, rpc.getDHT());
        kns.filter = KBucketEntry::eligibleForLocalLookup;
        kns.fill();
        todo.addCandidates(null, kns.getEntries());

        addListener(unused -> logClosest());

        super.start();
    }

    private void updatedPopulationEstimates() {
        synchronized (closest) {
            rpc.getDHT().getEstimator().update(closest.ids().collect(Collectors.toSet()), targetKey);
        }
    }
}
