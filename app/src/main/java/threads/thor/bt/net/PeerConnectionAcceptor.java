package threads.thor.bt.net;


public interface PeerConnectionAcceptor {

    /**
     * Makes an attempt to accept a new connection and returns a routine for establishing the connection.
     * Blocks until a new incoming connection is available.
     *
     * @return Routine for establishing the connection
     * @since 1.6
     */
    ConnectionRoutine accept();
}
