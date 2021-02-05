package threads.thor.magnet.protocol;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.PeerId;

/**
 * Standard handshake message.
 * This is the very first message that peers must send,
 * when initializing a new peer connection.
 * <p>
 * Handshake message includes:
 * - a constant header, specified in standard BitTorrent protocol
 * - threads.torrent ID
 * - peer ID
 * - 8 reserved bytes, that are used by extensions, e.g. BEP-10: Extension Protocol
 *
 * @since 1.0
 */
public final class Handshake implements Message {

    private static final int UPPER_RESERVED_BOUND = 8 * 8 - 1;

    private final byte[] reserved;
    private final TorrentId torrentId;
    private final PeerId peerId;

    /**
     * @since 1.0
     */
    public Handshake(byte[] reserved, TorrentId torrentId, PeerId peerId) throws InvalidMessageException {
        this.reserved = reserved;
        this.torrentId = torrentId;
        this.peerId = peerId;
    }

    /**
     * Check if a reserved bit is set.
     *
     * @param bitIndex Index of a bit to check (0..63 inclusive)
     * @return true if this bit is set to 1
     * @since 1.0
     */
    public boolean isReservedBitSet(int bitIndex) {
        return Protocols.getBit(reserved, BitOrder.LITTLE_ENDIAN, bitIndex) == 1;
    }

    /**
     * Set a reserved bit.
     *
     * @param bitIndex Index of a bit to set (0..63 inclusive)
     * @since 1.0
     */
    public void setReservedBit(int bitIndex) {
        if (bitIndex < 0 || bitIndex > UPPER_RESERVED_BOUND) {
            throw new RuntimeException("Illegal bit index: " + bitIndex +
                    ". Expected index in range [0.." + UPPER_RESERVED_BOUND + "]");
        }
        Protocols.setBit(reserved, BitOrder.LITTLE_ENDIAN, bitIndex);
        // check range
    }

    /**
     * @since 1.0
     */
    public byte[] getReserved() {
        return reserved;
    }

    /**
     * @since 1.0
     */
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * @since 1.0
     */
    public PeerId getPeerId() {
        return peerId;
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
