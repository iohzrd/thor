package threads.thor.magnet.protocol;

public final class Choke implements Message {

    private static final Choke instance = new Choke();

    private Choke() {
    }

    /**
     * @since 1.0
     */
    public static Choke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.CHOKE_ID;
    }
}
