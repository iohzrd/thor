package threads.thor.magnet.torrent;

import java.time.Duration;
import java.util.function.Consumer;

import threads.thor.magnet.protocol.Choke;
import threads.thor.magnet.protocol.Message;
import threads.thor.magnet.protocol.Unchoke;

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

        Boolean shouldChokeOptional = connectionState.getShouldChoke();
        boolean choking = connectionState.isChoking();
        boolean peerInterested = connectionState.isPeerInterested();

        if (shouldChokeOptional == null) {
            if (peerInterested && choking) {
                if (mightUnchoke(connectionState)) {
                    shouldChokeOptional = Boolean.FALSE; // should unchoke
                }
            } else if (!peerInterested && !choking) {
                shouldChokeOptional = Boolean.TRUE;
            }
        }

        if (shouldChokeOptional != null) {
            if (shouldChokeOptional != choking) {
                if (shouldChokeOptional) {
                    // choke immediately
                    connectionState.setChoking(true);
                    messageConsumer.accept(Choke.instance());
                    connectionState.setLastChoked(System.currentTimeMillis());
                } else if (mightUnchoke(connectionState)) {
                    connectionState.setChoking(false);
                    messageConsumer.accept(Unchoke.instance());
                }
            }
        }
    }

    private boolean mightUnchoke(ConnectionState connectionState) {
        // unchoke depending on last choked time to avoid fibrillation
        return System.currentTimeMillis() - connectionState.getLastChoked() >= CHOKING_THRESHOLD.toMillis();
    }
}
