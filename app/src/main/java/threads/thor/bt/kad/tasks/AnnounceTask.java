package threads.thor.bt.kad.tasks;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import threads.thor.bt.kad.DHTConstants;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.messages.AnnounceRequest;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.MessageBase.Method;

/**
 * @author Damokles
 */
public class AnnounceTask extends TargetedTask {

    private final NavigableMap<KBucketEntry, byte[]> todo;
    private final int port;
    private final AtomicInteger accepted = new AtomicInteger();
    private boolean isSeed;

    public AnnounceTask(RPCServer rpc, Node node,
                        Key info_hash, int port, Map<KBucketEntry, byte[]> candidatesAndTokens) {
        super(info_hash, rpc, node);
        this.port = port;
        this.todo = new TreeMap<>(new KBucketEntry.DistanceOrder(info_hash));
        todo.putAll(candidatesAndTokens);
    }

    public void setSeed(boolean isSeed) {
        this.isSeed = isSeed;
    }

    @Override
    void callFinished(RPCCall c, MessageBase rsp) {
        if (rsp.getType() != MessageBase.Type.RSP_MSG || rsp.getMethod() != Method.ANNOUNCE_PEER)
            return;
        if (!c.matchesExpectedID()) {
            return;
        }
        // strict port check
        if (!c.getRequest().getDestination().equals(rsp.getOrigin()))
            return;
        accepted.incrementAndGet();
    }

    @Override
    void callTimeout(RPCCall c) {
    }

    @Override
    void update() {
        for (; ; ) {
            if (getRecvResponses() >= DHTConstants.MAX_ENTRIES_PER_BUCKET)
                return;

            RequestPermit p = checkFreeSlot();
            // we don't care about stalls here;
            if (p != RequestPermit.FREE_SLOT)
                return;

            Map.Entry<KBucketEntry, byte[]> me = todo.firstEntry();

            if (me == null)
                return;

            KBucketEntry e = me.getKey();

            AnnounceRequest anr = new AnnounceRequest(targetKey, port, me.getValue());
            //System.out.println("sending announce to ID:"+e.getID()+" addr:"+e.getAddress());
            anr.setDestination(e.getAddress());
            anr.setSeed(isSeed);
            if (!rpcCall(anr, e.getID(), c -> {
                c.builtFromEntry(e);
                todo.entrySet().remove(me);
            })) {
                break;
            }
        }
    }

    @Override
    public int getTodoCount() {
        return todo.size();
    }

    @Override
    boolean canDoRequest() {
        // a) we only announce to K nodes, not N; b) wait out the full timeout, not he adaptive one
        return getNumOutstandingRequests() < DHTConstants.MAX_ENTRIES_PER_BUCKET;
    }

    @Override
    protected boolean isDone() {
        if (accepted.get() >= DHTConstants.MAX_ENTRIES_PER_BUCKET)
            return true;
        return todo.isEmpty() && getNumOutstandingRequests() == 0;
    }

}
