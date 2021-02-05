package threads.thor.magnet.torrent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class BlockWrite {

    private final boolean rejected;
    private final Throwable error;
    private final CompletableFuture<Boolean> verificationFuture;

    private BlockWrite(Throwable error,
                       boolean rejected,
                       CompletableFuture<Boolean> verificationFuture) {
        this.error = error;
        this.rejected = rejected;
        this.verificationFuture = verificationFuture;
    }

    /**
     * @since 1.9
     */
    static BlockWrite complete(CompletableFuture<Boolean> verificationFuture) {
        return new BlockWrite(null, false, verificationFuture);
    }

    /**
     * @since 1.9
     */
    static BlockWrite rejected() {
        return new BlockWrite(null, true, null);
    }

    /**
     * @since 1.9
     */
    static BlockWrite exceptional(Throwable error) {
        return new BlockWrite(error, false, null);
    }

    /**
     * @return true if the request was not accepted by the data worker
     * @since 1.0
     */
    public boolean isRejected() {
        return rejected;
    }

    /**
     * @return {@link Optional#empty()} if processing of the request completed normally,
     * or exception otherwise.
     * @since 1.0
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Get future, that will complete when the block is verified.
     * If future's boolean value is true, then verification was successful.
     *
     * @return Future or {@link Optional#empty()},
     * if {@link #isRejected()} returns true or {@link #getError()} is not empty.
     * @since 1.0
     */
    public Optional<CompletableFuture<Boolean>> getVerificationFuture() {
        return Optional.ofNullable(verificationFuture);
    }
}
