package threads.thor.bt.kad.tasks;

/**
 * @author Damokles
 */
public interface TaskListener {
    /**
     * The task is finsihed.
     *
     * @param t The Task
     */
    void finished(Task t);
}
