package threads.thor.magnet.protocol;

public final class Interested implements Message {

    private static final Interested instance = new Interested();

    private Interested() {
    }

    /**
     * @since 1.0
     */
    public static Interested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.INTERESTED_ID;
    }
}
