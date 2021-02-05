package threads.thor.magnet.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.PeerId;
import threads.thor.magnet.net.buffer.ByteBufferView;
import threads.thor.magnet.protocol.handler.BitfieldHandler;
import threads.thor.magnet.protocol.handler.CancelHandler;
import threads.thor.magnet.protocol.handler.ChokeHandler;
import threads.thor.magnet.protocol.handler.HaveHandler;
import threads.thor.magnet.protocol.handler.InterestedHandler;
import threads.thor.magnet.protocol.handler.MessageHandler;
import threads.thor.magnet.protocol.handler.NotInterestedHandler;
import threads.thor.magnet.protocol.handler.PieceHandler;
import threads.thor.magnet.protocol.handler.RequestHandler;
import threads.thor.magnet.protocol.handler.UnchokeHandler;

import static threads.thor.magnet.protocol.Protocols.readInt;

public class StandardBittorrentProtocol implements MessageHandler<Message> {

    /**
     * BitTorrent message ID size in bytes.
     *
     * @since 1.0
     */
    public static final int MESSAGE_TYPE_SIZE = 1;
    /**
     * @since 1.0
     */
    static final int CHOKE_ID = 0;
    /**
     * @since 1.0
     */
    static final int UNCHOKE_ID = 1;
    /**
     * @since 1.0
     */
    static final int INTERESTED_ID = 2;
    /**
     * @since 1.0
     */
    static final int NOT_INTERESTED_ID = 3;
    /**
     * @since 1.0
     */
    static final int HAVE_ID = 4;
    /**
     * @since 1.0
     */
    static final int BITFIELD_ID = 5;
    /**
     * @since 1.0
     */
    static final int REQUEST_ID = 6;
    /**
     * @since 1.0
     */
    static final int PIECE_ID = 7;
    /**
     * @since 1.0
     */
    static final int CANCEL_ID = 8;
    /**
     * BitTorrent message prefix size in bytes.
     *
     * @since 1.0
     */
    private static final int MESSAGE_LENGTH_PREFIX_SIZE = 4;
    /**
     * BitTorrent message prefix size in bytes.
     * Message prefix is a concatenation of message length prefix and message ID.
     *
     * @since 1.0
     */
    public static final int MESSAGE_PREFIX_SIZE = MESSAGE_LENGTH_PREFIX_SIZE + MESSAGE_TYPE_SIZE;
    private static final String PROTOCOL_NAME = "BitTorrent protocol";

    private static final byte[] PROTOCOL_NAME_BYTES;
    private static final byte[] HANDSHAKE_PREFIX;
    private static final int HANDSHAKE_RESERVED_OFFSET;
    private static final int HANDSHAKE_RESERVED_LENGTH = 8;

    private static final byte[] KEEPALIVE = new byte[]{0, 0, 0, 0};

    static {

        PROTOCOL_NAME_BYTES = PROTOCOL_NAME.getBytes(StandardCharsets.US_ASCII);
        int protocolNameLength = PROTOCOL_NAME_BYTES.length;
        int prefixLength = 1;

        HANDSHAKE_RESERVED_OFFSET = 1 + protocolNameLength;
        HANDSHAKE_PREFIX = new byte[HANDSHAKE_RESERVED_OFFSET];
        HANDSHAKE_PREFIX[0] = (byte) protocolNameLength;
        System.arraycopy(PROTOCOL_NAME_BYTES, 0, HANDSHAKE_PREFIX, prefixLength, protocolNameLength);
    }

    private final Map<Integer, MessageHandler<?>> handlers;
    private final Map<Integer, Class<? extends Message>> uniqueTypes;
    private final Map<Class<? extends Message>, MessageHandler<?>> handlersByType;
    private final Map<Class<? extends Message>, Integer> idMap;


    public StandardBittorrentProtocol(Map<Integer, MessageHandler<?>> extraHandlers) {

        Map<Integer, MessageHandler<?>> handlers = new HashMap<>();
        handlers.put(CHOKE_ID, new ChokeHandler());
        handlers.put(UNCHOKE_ID, new UnchokeHandler());
        handlers.put(INTERESTED_ID, new InterestedHandler());
        handlers.put(NOT_INTERESTED_ID, new NotInterestedHandler());
        handlers.put(HAVE_ID, new HaveHandler());
        handlers.put(BITFIELD_ID, new BitfieldHandler());
        handlers.put(REQUEST_ID, new RequestHandler());
        handlers.put(PIECE_ID, new PieceHandler());
        handlers.put(CANCEL_ID, new CancelHandler());

        extraHandlers.forEach((messageId, handler) -> {
            if (handlers.containsKey(messageId)) {
                throw new RuntimeException("Duplicate handler for message ID: " + messageId);
            }
            handlers.put(messageId, handler);
        });

        Map<Class<? extends Message>, Integer> idMap = new HashMap<>();
        Map<Class<? extends Message>, MessageHandler<?>> handlersByType = new HashMap<>();
        Map<Integer, Class<? extends Message>> uniqueTypes = new HashMap<>();

        handlers.forEach((messageId, handler) -> {

            if (handler.getSupportedTypes().isEmpty()) {
                throw new RuntimeException("No supported types declared in handler: " + handler.getClass().getName());
            } else {
                uniqueTypes.put(messageId, handler.getSupportedTypes().iterator().next());
            }

            handler.getSupportedTypes().forEach(messageType -> {
                        if (idMap.containsKey(messageType)) {
                            throw new RuntimeException("Duplicate handler for message type: " + messageType.getSimpleName());
                        }
                        idMap.put(messageType, messageId);
                        handlersByType.put(messageType, handler);
                    }
            );
        });

        this.handlers = handlers;
        this.idMap = idMap;
        this.handlersByType = handlersByType;
        this.uniqueTypes = uniqueTypes;
    }

    // keep-alive: <len=0000>
    private static boolean writeKeepAlive(ByteBuffer buffer) {
        if (buffer.remaining() < KEEPALIVE.length) {
            return false;
        }
        buffer.put(KEEPALIVE);
        return true;
    }

    // handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
    private static boolean writeHandshake(ByteBuffer buffer, byte[] reserved, TorrentId torrentId, PeerId peerId) {

        if (reserved.length != HANDSHAKE_RESERVED_LENGTH) {
            throw new InvalidMessageException("Invalid reserved bytes: expected " + HANDSHAKE_RESERVED_LENGTH
                    + " bytes, received " + reserved.length);
        }

        int length = HANDSHAKE_PREFIX.length + HANDSHAKE_RESERVED_LENGTH +
                TorrentId.length() + PeerId.length();
        if (buffer.remaining() < length) {
            return false;
        }

        buffer.put(HANDSHAKE_PREFIX);
        buffer.put(reserved);
        buffer.put(torrentId.getBytes());
        buffer.put(peerId.getBytes());

        return true;
    }

    private static int decodeHandshake(DecodingContext context, ByteBufferView buffer) {

        int consumed = 0;
        int length = HANDSHAKE_RESERVED_LENGTH + TorrentId.length() + PeerId.length();
        int limit = HANDSHAKE_RESERVED_OFFSET + length;

        if (buffer.remaining() >= limit) {

            buffer.get(); // skip message ID

            byte[] protocolNameBytes = new byte[PROTOCOL_NAME.length()];
            buffer.get(protocolNameBytes);
            if (!Arrays.equals(PROTOCOL_NAME_BYTES, protocolNameBytes)) {
                throw new InvalidMessageException("Unexpected protocol name (decoded with ASCII): " +
                        new String(protocolNameBytes, StandardCharsets.US_ASCII));
            }

            byte[] reserved = new byte[HANDSHAKE_RESERVED_LENGTH];
            buffer.get(reserved);

            byte[] infoHash = new byte[TorrentId.length()];
            buffer.get(infoHash);

            byte[] peerId = new byte[PeerId.length()];
            buffer.get(peerId);

            context.setMessage(new Handshake(reserved, TorrentId.fromBytes(infoHash), PeerId.fromBytes(peerId)));
            consumed = limit;
        }

        return consumed;
    }

    @Override
    public Collection<Class<? extends Message>> getSupportedTypes() {
        return null;
    }

    @Override
    public final Class<? extends Message> readMessageType(ByteBufferView buffer) {

        Objects.requireNonNull(buffer);

        if (!buffer.hasRemaining()) {
            return null;
        }

        int position = buffer.position();
        byte first = buffer.get();
        if (first == PROTOCOL_NAME.length()) {
            return Handshake.class;
        }
        buffer.position(position);

        Integer length = readInt(buffer);
        if (length == null) {
            return null;
        } else if (length == 0) {
            return KeepAlive.class;
        }

        if (buffer.hasRemaining()) {
            int messageTypeId = buffer.get();
            Class<? extends Message> messageType;

            messageType = uniqueTypes.get(messageTypeId);
            if (messageType == null) {
                MessageHandler<?> handler = handlers.get(messageTypeId);
                if (handler == null) {
                    throw new InvalidMessageException("Unknown message type ID: " + messageTypeId);
                }
                messageType = handler.readMessageType(buffer);
            }
            return messageType;
        }

        return null;
    }

    @Override
    public final int decode(DecodingContext context, ByteBufferView buffer) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(buffer);

        if (!buffer.hasRemaining()) {
            return 0;
        }

        int position = buffer.position();
        Class<? extends Message> messageType = readMessageType(buffer);
        if (messageType == null) {
            return 0;
        }

        if (Handshake.class.equals(messageType)) {
            buffer.position(position);
            return decodeHandshake(context, buffer);
        }
        if (KeepAlive.class.equals(messageType)) {
            context.setMessage(KeepAlive.instance());
            return KEEPALIVE.length;
        }

        MessageHandler<?> handler = Objects.requireNonNull(handlersByType.get(messageType));
        buffer.position(position);
        return handler.decode(context, buffer);
    }

    @Override
    public final boolean encode(EncodingContext context, Message message, ByteBuffer buffer) {

        Objects.requireNonNull(buffer);
        Integer messageId = idMap.get(Objects.requireNonNull(message).getClass());

        if (Handshake.class.equals(message.getClass())) {
            Handshake handshake = (Handshake) message;
            return writeHandshake(buffer, handshake.getReserved(), handshake.getTorrentId(), handshake.getPeerId());
        }
        if (KeepAlive.class.equals(message.getClass())) {
            return writeKeepAlive(buffer);
        }

        if (messageId == null) {
            throw new InvalidMessageException("Unknown message type: " + message.getClass().getSimpleName());
        }
        return ((MessageHandler<Message>) handlers.get(messageId)).encode(context, message, buffer);
    }
}
