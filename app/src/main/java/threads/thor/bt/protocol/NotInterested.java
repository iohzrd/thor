package threads.thor.bt.protocol;

public final class NotInterested implements Message {

    private static final NotInterested instance = new NotInterested();

    private NotInterested() {
    }

    /**
     * @since 1.0
     */
    public static NotInterested instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.NOT_INTERESTED_ID;
    }
}
