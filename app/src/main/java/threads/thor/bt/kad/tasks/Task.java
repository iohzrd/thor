package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import threads.LogUtils;
import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.DHTConstants;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCCallListener;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.RPCState;
import threads.thor.bt.kad.SerializedTaskExecutor;
import threads.thor.bt.kad.messages.MessageBase;

import static threads.thor.bt.kad.tasks.CountedStat.FAILED;
import static threads.thor.bt.kad.tasks.CountedStat.RECEIVED;
import static threads.thor.bt.kad.tasks.CountedStat.SENT;
import static threads.thor.bt.kad.tasks.CountedStat.SENT_SINCE_RECEIVE;
import static threads.thor.bt.kad.tasks.CountedStat.STALLED;

/**
 * Performs a task on K nodes provided by a KClosestNodesSearch.
 * This is a base class for all tasks.
 *
 * @author Damokles
 */
public abstract class Task implements Comparable<Task> {
    static final String TAG = Task.class.getSimpleName();
    final AtomicReference<TaskStats> counts = new AtomicReference<>(new TaskStats());
    final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.INITIAL);
    private final RPCCallListener postProcessingListener = new RPCCallListener() {
        public void stateTransition(RPCCall c, RPCState previous, RPCState current) {

            switch (current) {
                case RESPONDED:
                case TIMEOUT:
                case STALLED:
                case ERROR:
                    serializedUpdate.run();
                    break;
                default:
                    break;
            }

        }
    };
    Node node;
    Set<RPCCall> inFlight;
    private final RPCCallListener preProcessingListener = new RPCCallListener() {
        public void stateTransition(RPCCall c, RPCState previous, RPCState current) {

            counts.updateAndGet(cnt -> {
                EnumSet<CountedStat> inc = EnumSet.noneOf(CountedStat.class);
                EnumSet<CountedStat> dec = EnumSet.noneOf(CountedStat.class);
                EnumSet<CountedStat> zero = EnumSet.noneOf(CountedStat.class);

                if (previous == RPCState.STALLED)
                    dec.add(STALLED);
                if (current == RPCState.STALLED)
                    inc.add(STALLED);

                if (current == RPCState.RESPONDED) {
                    inc.add(RECEIVED);
                    zero.add(SENT_SINCE_RECEIVE);
                }

                if (current == RPCState.TIMEOUT || current == RPCState.ERROR)
                    inc.add(FAILED);


                return cnt.update(inc, dec, zero);
            });

            switch (current) {
                case RESPONDED:
                    inFlight.remove(c);
                    if (!isFinished())
                        callFinished(c, c.getResponse());
                    break;
                case ERROR:
                    inFlight.remove(c);
                    break;
                case TIMEOUT:
                    inFlight.remove(c);
                    if (!isFinished())
                        callTimeout(c);
                    break;
                default:
                    break;
            }


        }
    };
    long firstResultTime;
    RPCServer rpc;
    private String info;
    private long startTime;
    private int taskID;
    private List<TaskListener> listeners;
    private long finishTime;
    private final Runnable serializedUpdate = SerializedTaskExecutor.onceMore(this::runStuff);
    private boolean lowPriority;

    /**
     * Create a task.
     *
     * @param rpc  The RPC server to do RPC calls
     * @param node The node
     */
    Task(RPCServer rpc, Node node) {
        if (rpc == null)
            throw new IllegalArgumentException("RPC must not be null");

        this.rpc = rpc;
        this.node = node;

        inFlight = ConcurrentHashMap.newKeySet();
    }

    boolean setState(TaskState expected, TaskState newState) {
        return setState(EnumSet.of(expected), newState);
    }

    private boolean setState(Set<TaskState> expected, TaskState newState) {
        TaskState current;
        do {
            current = state.get();
            if (!expected.contains(current))
                return false;

        } while (!state.weakCompareAndSet(current, newState));

        return true;

    }

    public RPCServer getRPC() {
        return rpc;
    }

    public int compareTo(Task o) {
        return taskID - o.taskID;
    }

    @Override
    public int hashCode() {
        return taskID;
    }

    /**
     * Start the task, to be used when a task is queued.
     */
    public void start() {
        if (setState(EnumSet.of(TaskState.INITIAL, TaskState.QUEUED), TaskState.RUNNING)) {
            LogUtils.debug(TAG, "Starting Task: " + toString());
            startTime = System.currentTimeMillis();
            try {
                serializedUpdate.run();
            } catch (Exception e) {
                DHT.log(e);
            }
        }
    }

    private void runStuff() {
        if (isDone())
            finish();

        if (canDoRequest() && !isFinished()) {
            update();

            // check again in case todo-queue has been drained by update()
            if (isDone())
                finish();
        }


    }

    /**
     * Will continue the task, this will be called every time we have
     * rpc slots available for this task. Should be implemented by derived classes.
     */
    abstract void update();

    /**
     * A call is finished and a response was received.
     *
     * @param c   The call
     * @param rsp The response
     */
    abstract void callFinished(RPCCall c, MessageBase rsp);

    /**
     * A call timedout
     *
     * @param c The call
     */
    abstract void callTimeout(RPCCall c);

    /**
     * Do a call to the rpc server, increments the outstanding_reqs variable.
     *
     * @param req THe request to send
     * @return true if call was made, false if not
     */
    boolean rpcCall(MessageBase req, Key expectedID, Consumer<RPCCall> modifyCallBeforeSubmit) {
        if (!canDoRequest()) {
            // if we reject a request we need something to wakeup the task later
            rpc.onDeclog(serializedUpdate);
            return false;
        }

        RPCCall call = new RPCCall(req).setExpectedID(expectedID);

        // bump counters early to ensure task stays alive
        counts.updateAndGet(cnt -> cnt.update(EnumSet.of(SENT, SENT_SINCE_RECEIVE), EnumSet.noneOf(CountedStat.class), EnumSet.noneOf(CountedStat.class)));

        call.addListener(preProcessingListener);

        if (modifyCallBeforeSubmit != null)
            modifyCallBeforeSubmit.accept(call);

        call.addListener(postProcessingListener);

        inFlight.add(call);

        // asyncify since we're under a lock here
        rpc.getDHT().getScheduler().execute(() -> rpc.doCall(call));

        return true;
    }


    public void setLowPriority(boolean lowPriority) {
        this.lowPriority = lowPriority;
    }

    public int requestConcurrency() {
        return lowPriority ? DHTConstants.MAX_CONCURRENT_REQUESTS_LOWPRIO : DHTConstants.MAX_CONCURRENT_REQUESTS;
    }

    RequestPermit checkFreeSlot() {
        TaskStats stats = counts.get();
        int activeOnly = stats.activeOnly();
        int activeAndStalled = stats.unanswered();
        int concurrency = requestConcurrency();

        // based on measurements the expected loss rate is ~50% on average (see RPCServer)
        // if we exceed that (+margin) don't let stalls trigger additional requests, wait for new responses/full timeouts
        if (activeAndStalled >= concurrency && stats.get(RECEIVED) * 3 < stats.get(SENT))
            return RequestPermit.NONE_ALLOWED;

        if (activeAndStalled < concurrency)
            return RequestPermit.FREE_SLOT;

        if (activeOnly < concurrency /*&& stats.get(SENT_SINCE_RECEIVE) < concurrency*/)
            return RequestPermit.FREE_STALL_SLOT;

        return RequestPermit.NONE_ALLOWED;
    }

    /// See if we can do a request
    boolean canDoRequest() {
        return checkFreeSlot() != RequestPermit.NONE_ALLOWED;
    }

    boolean hasUnfinishedRequests() {
        return counts.get().unanswered() > 0;
    }

    /// Is the task finished
    public boolean isFinished() {
        return state.get().isTerminal();
    }

    /// Get the task ID
    int getTaskID() {
        return taskID;
    }

    /// Set the task ID
    void setTaskID(int tid) {
        taskID = tid;
    }

    /**
     * @return the Count of Failed Requests
     */
    public int getFailedReqs() {
        return counts.get().get(FAILED);
    }

    /**
     * @return the Count of Received Responses
     */
    int getRecvResponses() {
        return counts.get().get(RECEIVED);
    }

    /**
     * @return the Count of Sent Requests
     */
    public int getSentReqs() {
        return counts.get().get(SENT);
    }

    abstract public int getTodoCount();

    /**
     * @return the info
     */
    public String getInfo() {
        return info;
    }

    /**
     * @param info the info to set
     */
    public void setInfo(String info) {
        this.info = info;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getFinishedTime() {
        return finishTime;
    }

    public long getFirstResultTime() {
        return firstResultTime;
    }

    /**
     * @return number of requests that this task is actively waiting for
     */
    int getNumOutstandingRequestsExcludingStalled() {
        return counts.get().activeOnly();
    }

    /**
     * @return number of requests that still haven't reached their final state but might have stalled
     */
    int getNumOutstandingRequests() {
        return counts.get().unanswered();
    }

    public boolean isQueued() {
        return state.get() == TaskState.QUEUED;
    }

    /// Kills the task
    public void kill() {
        if (setState(EnumSet.complementOf(EnumSet.of(TaskState.FINISHED, TaskState.KILLED)), TaskState.KILLED))
            notifyCompletionListeners();
    }

    private void finish() {
        if (setState(EnumSet.complementOf(EnumSet.of(TaskState.FINISHED, TaskState.KILLED)), TaskState.FINISHED))
            notifyCompletionListeners();
    }

    private void notifyCompletionListeners() {
        finishTime = System.currentTimeMillis();

        if (listeners != null) {
            for (TaskListener tl : listeners) {
                tl.finished(this);
            }
        }
    }

    protected abstract boolean isDone();

    public void addListener(TaskListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>(1);
        }
        // listener is added after the task already terminated, thus it won't get the event, trigger it manually
        if (state.get().isTerminal())
            listener.finished(this);
        listeners.add(listener);
    }

    public void removeListener(TaskListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    Duration age() {
        return Duration.between(Instant.ofEpochMilli(startTime), Instant.now());
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(100);
        TaskStats stats = counts.get();
        b.append(this.getClass().getSimpleName());
        b.append(' ').append(getTaskID());
        if (this instanceof TargetedTask)
            b.append(" target:").append(((TargetedTask) this).getTargetKey());
        b.append(" todo:").append(getTodoCount());
        if (!state.get().preStart()) {
            //b.append(" sent:").append(stats.get(SENT));
            //b.append(" recv:").append(stats.get(RECEIVED));
            b.append(" ").append(stats);

        }
        b.append(" srv: ").append(rpc.getDerivedID());

        b.append(' ').append(state.get().toString());

        if (startTime != 0) {
            if (finishTime == 0)
                b.append(" age:").append(age());
            else if (finishTime > 0)
                b.append(" time to finish:").append(Duration.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(finishTime)));
        }

        b.append(" name:").append(info);

        return b.toString();
    }

    public enum TaskState {
        INITIAL,
        QUEUED,
        RUNNING,
        FINISHED,
        KILLED;

        boolean isTerminal() {
            return this == FINISHED || this == KILLED;
        }


        boolean preStart() {
            return this == INITIAL || this == QUEUED;
        }

    }

    enum RequestPermit {
        NONE_ALLOWED,
        FREE_SLOT,
        FREE_STALL_SLOT
    }

    interface CandidateSupplier {
        boolean has();

        KBucketEntry current();

        void remove(KBucketEntry e);
    }
}
