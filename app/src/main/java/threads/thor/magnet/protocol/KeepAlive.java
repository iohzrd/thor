package threads.thor.magnet.protocol;

public final class KeepAlive implements Message {

    private static final KeepAlive instance = new KeepAlive();

    private KeepAlive() {
    }

    /**
     * @since 1.0
     */
    public static KeepAlive instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        throw new UnsupportedOperationException();
    }
}
