package threads.lite.core;

public class ClosedException extends Exception {
    public ClosedException() {
        super("Context closed");
    }
}
