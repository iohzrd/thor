package threads.thor.bt.torrent.messaging;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import threads.thor.bt.protocol.Choke;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.Unchoke;

class Choker {

    private static final Duration CHOKING_THRESHOLD = Duration.ofMillis(10000);

    private static final Choker instance = new Choker();

    /**
     * @return Choker instance
     * @since 1.0
     */
    public static Choker choker() {
        return instance;
    }

    /**
     * Inspects connection state and yields choke/unchoke messages when appropriate.
     *
     * @param connectionState Connection state for the choker
     *                        to inspect and update choked/unchoked status.
     * @param messageConsumer Message worker
     * @since 1.0
     */
    public void handleConnection(ConnectionState connectionState, Consumer<Message> messageConsumer) {

        Optional<Boolean> shouldChokeOptional = connectionState.getShouldChoke();
        boolean choking = connectionState.isChoking();
        boolean peerInterested = connectionState.isPeerInterested();

        if (!shouldChokeOptional.isPresent()) {
            if (peerInterested && choking) {
                if (mightUnchoke(connectionState)) {
                    shouldChokeOptional = Optional.of(Boolean.FALSE); // should unchoke
                }
            } else if (!peerInterested && !choking) {
                shouldChokeOptional = Optional.of(Boolean.TRUE);
            }
        }

        shouldChokeOptional.ifPresent(shouldChoke -> {
            if (shouldChoke != choking) {
                if (shouldChoke) {
                    // choke immediately
                    connectionState.setChoking(true);
                    messageConsumer.accept(Choke.instance());
                    connectionState.setLastChoked(System.currentTimeMillis());
                } else if (mightUnchoke(connectionState)) {
                    connectionState.setChoking(false);
                    messageConsumer.accept(Unchoke.instance());
                }
            }
        });
    }

    private boolean mightUnchoke(ConnectionState connectionState) {
        // unchoke depending on last choked time to avoid fibrillation
        return System.currentTimeMillis() - connectionState.getLastChoked() >= CHOKING_THRESHOLD.toMillis();
    }
}
