package threads.thor.magnet.net;

public interface ConnectionRoutine {

    /**
     * Try to establish the connection.
     *
     * @since 1.6
     */
    ConnectionResult establish();

    /**
     * Cancel connection establishing and release related resources.
     *
     * @since 1.6
     */
    void cancel();
}
