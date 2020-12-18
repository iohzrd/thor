package threads.thor.bt.net;

import java.util.Objects;

public class ConnectionResult {

    private final PeerConnection connection;
    private final Throwable error;
    private final String message;

    private ConnectionResult(PeerConnection connection,
                             Throwable error,
                             String message) {
        this.connection = connection;
        this.error = error;
        this.message = message;
    }


    public static ConnectionResult success(PeerConnection connection) {
        Objects.requireNonNull(connection);
        return new ConnectionResult(connection, null, null);
    }

    /**
     * @since 1.6
     */
    public static ConnectionResult failure(String message, Throwable error) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(error);
        return new ConnectionResult(null, error, message);
    }

    /**
     * @since 1.6
     */
    public static ConnectionResult failure(String message) {
        Objects.requireNonNull(message);
        return new ConnectionResult(null, null, message);
    }

    /**
     * @return true, if the connection attempt has been successful
     * @since 1.6
     */
    public boolean isSuccess() {
        return connection != null;
    }

    /**
     * @return Connection, if {@link #isSuccess()} is true
     * @throws IllegalStateException if {@link #isSuccess()} is false
     */
    public PeerConnection getConnection() {
        if (!isSuccess()) {
            throw new IllegalStateException("Attempt to retrieve connection from unsuccessful result");
        }
        return connection;
    }

}
