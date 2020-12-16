package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import threads.thor.bt.kad.KBucket;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.PingRequest;

/**
 * @author Damokles
 */
public class PingRefreshTask extends Task {

    private final Map<MessageBase, KBucketEntry> lookupMap = new HashMap<>();
    private final Deque<KBucketEntry> todo;
    private final Set<KBucketEntry> visited;
    private final boolean cleanOnTimeout;
    private boolean alsoCheckGood = false;
    private boolean probeReplacement = false;
    private KBucket bucket;


    public PingRefreshTask(@NonNull RPCServer rpc, Node node, KBucket bucket, boolean cleanOnTimeout) {
        super(rpc, node);
        this.cleanOnTimeout = cleanOnTimeout;
        todo = new ArrayDeque<>();
        visited = new HashSet<>();

        addBucket(bucket);
    }

    public void checkGoodEntries(boolean val) {
        alsoCheckGood = val;
    }

    public void probeUnverifiedReplacement() {
        probeReplacement = true;
    }

    public void addBucket(KBucket bucket) {
        if (bucket != null) {
            if (this.bucket != null)
                throw new IllegalStateException("a bucket already present");
            this.bucket = bucket;
            bucket.updateRefreshTimer();
            for (KBucketEntry e : bucket.getEntries()) {
                if (e.needsPing() || cleanOnTimeout || alsoCheckGood) {
                    todo.add(e);
                }
            }

            if (probeReplacement) {
                bucket.findPingableReplacement().ifPresent(todo::add);
            }

        }
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#callFinished(threads.thor.bt.kad.RPCCallBase, threads.thor.bt.kad.messages.MessageBase)
     */
    @Override
    void callFinished(RPCCall c, MessageBase rsp) {
        // most of the success handling is done by bucket maintenance
        synchronized (lookupMap) {
            lookupMap.remove(c.getRequest());
        }
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.Task#callTimeout(threads.thor.bt.kad.RPCCallBase)
     */
    @Override
    void callTimeout(RPCCall c) {
        MessageBase mb = c.getRequest();

        synchronized (lookupMap) {
            KBucketEntry e = lookupMap.remove(mb);
            if (e == null)
                return;

            KBucket bucket = node.table().entryForId(e.getID()).getBucket();
            if (bucket != null) {
                if (cleanOnTimeout) {
                    bucket.removeEntryIfBad(e, true);
                }
            }
        }

    }

    @Override
    public int getTodoCount() {
        return todo.size();
    }

    @Override
    void update() {
        if (todo.isEmpty()) {
            bucket.entriesStream().filter(KBucketEntry::needsPing).filter(e -> !lookupMap.containsValue(e)).forEach(todo::add);
        }

        while (!todo.isEmpty() && canDoRequest()) {
            KBucketEntry e = todo.peekFirst();
            Objects.requireNonNull(e);
            if (visited.contains(e) || (!alsoCheckGood && !e.needsPing())) {
                todo.remove(e);
                continue;
            }

            PingRequest pr = new PingRequest();
            pr.setDestination(e.getAddress());

            if (!rpcCall(pr, e.getID(), c -> {
                c.builtFromEntry(e);
                synchronized (lookupMap) {
                    lookupMap.put(pr, e);
                }
                visited.add(e);
                todo.remove(e);
            })) {
                break;
            }

        }
    }

    @Override
    protected boolean isDone() {
        return todo.isEmpty() && getNumOutstandingRequests() == 0 && !isFinished();
    }
}
