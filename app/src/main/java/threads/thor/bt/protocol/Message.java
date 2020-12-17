package threads.thor.bt.protocol;

public interface Message {

    /**
     * @return Unique message ID, as defined by the standard BitTorrent protocol.
     * @since 1.0
     */
    Integer getMessageId();
}
