package threads.thor.bt.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import threads.thor.bt.torrent.TorrentSessionState;

public interface BtClient {

    /**
     * Start threads.torrent processing asynchronously in a separate thread.
     *
     * @return Future, that can be joined by the calling thread
     * or used in any other way, which is convenient for the caller.
     * @since 1.0
     */
    CompletableFuture<?> startAsync();

    /**
     * Start threads.torrent processing asynchronously in a separate thread
     * and schedule periodic callback invocations.
     *
     * @param listener Callback, that is periodically provided
     *                 with an up-to-date state of threads.torrent session.
     * @param period   Interval at which the listener should be invoked, in milliseconds.
     * @return Future, that can be joined by the calling thread
     * or used in any other way, which is convenient for the caller.
     * @since 1.0
     */
    CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period);

    /**
     * Stop threads.torrent processing.
     *
     * @since 1.0
     */
    void stop();

    /**
     * Check if this client is started.
     *
     * @return true if this client is started
     * @since 1.1
     */
    boolean isStarted();
}
