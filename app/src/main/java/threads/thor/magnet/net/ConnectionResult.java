package threads.thor.magnet.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class ConnectionResult {

    @Nullable
    private final PeerConnection connection;


    private ConnectionResult(@Nullable PeerConnection connection) {
        this.connection = connection;

    }


    public static ConnectionResult success(@NonNull PeerConnection connection) {
        Objects.requireNonNull(connection);
        return new ConnectionResult(connection);
    }


    public static ConnectionResult failure() {
        return new ConnectionResult(null);
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
