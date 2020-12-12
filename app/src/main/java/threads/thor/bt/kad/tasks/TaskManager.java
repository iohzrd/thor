package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.DHTConstants;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.tasks.Task.TaskState;

/**
 * Manages all dht tasks.
 *
 * @author Damokles
 */
public class TaskManager {

    private final ConcurrentHashMap<RPCServer, ServerSet> taskSets;
    private final DHT dht;
    private final AtomicInteger next_id = new AtomicInteger();
    private final TaskListener finishListener;

    public TaskManager(@NonNull DHT dht) {
        this.dht = dht;


        taskSets = new ConcurrentHashMap<>();
        next_id.set(1);

        finishListener = t -> {
            dht.getStats().taskFinished(t);
            setFor(t.getRPC()).ifPresent(s -> {
                synchronized (s.active) {
                    s.active.remove(t);
                }
                s.dequeue();

            });
        };
    }

    public void addTask(Task task) {
        addTask(task, false);
    }

    private Optional<ServerSet> setFor(RPCServer srv) {
        if (srv.getState() != RPCServer.State.RUNNING)
            return Optional.empty();
        return Optional.ofNullable(taskSets.computeIfAbsent(srv, k -> {
            ServerSet ss = new ServerSet();
            ss.server = k;
            return ss;
        }));
    }

    public void dequeue(RPCServer k) {
        setFor(k).ifPresent(ServerSet::dequeue);
    }

    public void dequeue() {
        for (RPCServer srv : taskSets.keySet())
            setFor(srv).ifPresent(ServerSet::dequeue);
    }


    public void addTask(Task task, boolean isPriority) {
        int id = next_id.incrementAndGet();
        task.addListener(finishListener);
        task.setTaskID(id);
        Optional<ServerSet> s = setFor(task.getRPC());
        if (!s.isPresent()) {
            task.kill();
            return;
        }
        if (task.state.get() == TaskState.RUNNING) {
            synchronized (s.get().active) {
                s.get().active.add(task);
            }
            return;
        }

        if (!task.setState(TaskState.INITIAL, TaskState.QUEUED))
            return;

        synchronized (s.get().queued) {
            if (isPriority)
                s.get().queued.addFirst(task);
            else
                s.get().queued.addLast(task);
        }
    }

    public void removeServer(RPCServer srv) {
        ServerSet set = taskSets.get(srv);
        if (set == null)
            return;
        taskSets.remove(srv);

        synchronized (set.active) {
            set.active.forEach(Task::kill);
        }

        synchronized (set.queued) {
            set.queued.forEach(Task::kill);
        }
    }

    /// Get the number of running tasks
    public int getNumTasks() {
        return taskSets.values().stream().mapToInt(s -> s.active.size()).sum();
    }

    /// Get the number of queued tasks
    public int getNumQueuedTasks() {
        return taskSets.values().stream().mapToInt(s -> s.queued.size()).sum();
    }

    public Task[] getActiveTasks() {
        Task[] t = taskSets.values().stream().flatMap(s -> s.snapshotActive().stream()).toArray(Task[]::new);
        Arrays.sort(t);
        return t;
    }

    public Task[] getQueuedTasks() {
        return taskSets.values().stream().flatMap(s -> s.snapshotQueued().stream()).toArray(Task[]::new);
    }


    @NonNull
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("next id: ").append(next_id).append('\n');
        b.append("#### active: \n");

        for (Task t : getActiveTasks())
            b.append(t.toString()).append('\n');

        b.append("#### queued: \n");

        for (Task t : getQueuedTasks())
            b.append(t.toString()).append('\n');


        return b.toString();
    }

    class ServerSet {
        final Deque<Task> queued = new ArrayDeque<>();
        final List<Task> active = new ArrayList<>();
        RPCServer server;

        void dequeue() {
            while (true) {
                Task t;
                synchronized (queued) {
                    t = queued.peekFirst();
                    if (t == null)
                        break;
                    if (!canStartTask(t.getRPC()))
                        break;
                    queued.removeFirst();
                }
                if (t.isFinished())
                    continue;

                synchronized (active) {
                    active.add(t);
                }
                dht.getScheduler().execute(t::start);
            }
        }

        boolean canStartTask(RPCServer srv) {
            // we can start a task if we have less then  7 runnning per server and
            // there are at least 16 RPC slots available

            int activeCalls = srv.getNumActiveRPCCalls();
            if (activeCalls + 16 >= DHTConstants.MAX_ACTIVE_CALLS)
                return false;

            int perServer = active.size();

            if (perServer < DHTConstants.MAX_ACTIVE_TASKS)
                return true;

            if (activeCalls >= (DHTConstants.MAX_ACTIVE_CALLS * 2) / 3)
                return false;
            // if all their tasks have sent at least their initial volley and we still have enough head room we can allow more tasks.
            synchronized (active) {
                return active.stream().allMatch(t -> t.requestConcurrency() < t.getSentReqs());
            }
        }

        Collection<Task> snapshotActive() {
            synchronized (active) {
                return new ArrayList<>(active);
            }
        }

        Collection<Task> snapshotQueued() {
            synchronized (queued) {
                return new ArrayList<>(queued);
            }
        }

    }

}
