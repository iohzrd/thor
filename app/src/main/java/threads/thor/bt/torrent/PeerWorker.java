package threads.thor.bt.torrent;

import java.util.function.Consumer;
import java.util.function.Supplier;

import threads.thor.bt.protocol.Message;

/**
 * Instances of this class are responsible for providing a messaging interface
 * with one particular peer within a threads.torrent processing session.
 *
 * @since 1.0
 */
public interface PeerWorker extends Consumer<Message>, Supplier<Message> {

    /**
     * @return Current state of the connection
     * @since 1.0
     */
    ConnectionState getConnectionState();
}
