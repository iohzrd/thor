package threads.thor.bt.kad.tasks;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import threads.thor.bt.kad.DHT.DHTtype;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.Node.RoutingTableEntry;
import threads.thor.bt.kad.NodeList;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.messages.FindNodeRequest;
import threads.thor.bt.kad.messages.FindNodeResponse;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.MessageBase.Method;
import threads.thor.bt.kad.messages.MessageBase.Type;

/**
 * @author The 8472
 */
public class KeyspaceCrawler extends Task {

    private final Set<InetSocketAddress> responded = new HashSet<>();
    private final Set<KBucketEntry> todo = new HashSet<>();
    private final Set<InetSocketAddress> visited = new HashSet<>();

    KeyspaceCrawler(RPCServer rpc, Node node) {
        super(rpc, node);
        setInfo("Exhaustive Keyspace Crawl");
        addListener(t -> done());
    }

    @Override
    public int getTodoCount() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    void update() {
        // go over the todo list and send find node calls
        // until we have nothing left

        while (canDoRequest()) {
            synchronized (todo) {

                KBucketEntry e = todo.stream().findAny().orElse(null);
                if (e == null)
                    break;

                if (visited.contains(e.getAddress()))
                    continue;

                // send a findNode to the node
                FindNodeRequest fnr;

                fnr = new FindNodeRequest(Key.createRandomKey());
                fnr.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT);
                fnr.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT);
                fnr.setDestination(e.getAddress());
                rpcCall(fnr, e.getID(), c -> {
                    todo.remove(e);
                    visited.add(e.getAddress());
                });
            }
        }
    }

    @Override
    void callFinished(RPCCall c, MessageBase rsp) {
        if (isFinished()) {
            return;
        }

        // check the response and see if it is a good one
        if (rsp.getMethod() != Method.FIND_NODE || rsp.getType() != Type.RSP_MSG)
            return;

        FindNodeResponse fnr = (FindNodeResponse) rsp;

        responded.add(fnr.getOrigin());

        NodeList nodes = fnr.getNodes(rpc.getDHT().getType());
        if (nodes == null)
            return;

        synchronized (todo) {
            nodes.entries().filter(e -> !node.isLocalId(e.getID()) && !todo.contains(e) && visited.contains(e.getAddress())).forEach(todo::add);
        }


    }

    @Override
    public int requestConcurrency() {
        // TODO Auto-generated method stub
        return super.requestConcurrency() * 5;
    }

    @Override
    protected boolean isDone() {
        return todo.size() == 0 && getNumOutstandingRequests() == 0 && !isFinished();
    }

    @Override
    void callTimeout(RPCCall c) {

    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#start()
     */
    @Override
    public void start() {
        int added = 0;

        // delay the filling of the todo list until we actually start the task

        for (RoutingTableEntry bucket : node.table().list())
            for (KBucketEntry e : bucket.getBucket().getEntries())
                if (e.eligibleForLocalLookup()) {
                    todo.add(e);
                    added++;
                }
        super.start();
    }


    private void done() {
        System.out.println("crawler done, seen " + responded.size());
    }
}
