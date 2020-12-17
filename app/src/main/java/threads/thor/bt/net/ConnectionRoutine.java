package threads.thor.bt.net;

import java.net.SocketAddress;

public interface ConnectionRoutine {

    /**
     * @since 1.6
     */
    SocketAddress getRemoteAddress();

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
