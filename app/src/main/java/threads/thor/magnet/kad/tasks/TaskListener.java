package threads.thor.magnet.kad.tasks;

public interface TaskListener {
    /**
     * The task is finsihed.
     *
     * @param t The Task
     */
    void finished(Task t);
}
