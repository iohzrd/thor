package threads.thor.magnet.protocol;

public class InvalidMessageException extends RuntimeException {

    /**
     * @since 1.0
     */
    public InvalidMessageException(String message) {
        super(message);
    }
}