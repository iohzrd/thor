package threads.thor.bt.protocol;

public final class Unchoke implements Message {

    private static final Unchoke instance = new Unchoke();

    private Unchoke() {
    }

    /**
     * @since 1.0
     */
    public static Unchoke instance() {
        return instance;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "]";
    }

    @Override
    public Integer getMessageId() {
        return StandardBittorrentProtocol.UNCHOKE_ID;
    }
}
