package threads.thor.magnet.protocol;

import java.util.Collection;

import threads.thor.magnet.net.buffer.ByteBufferView;

public interface Protocol<T> {

    /**
     * @return All message types, supported by this protocol.
     * @since 1.0
     */
    Collection<Class<? extends T>> getSupportedTypes();

    /**
     * Tries to determine the message type based on the (part of the) message available in the byte buffer.
     *
     * @param buffer Byte buffer of arbitrary length containing (a part of) the message.
     *               Decoding should be performed starting with the current position of the buffer.
     * @return Message type or @{code null} if the data is insufficient
     * @throws InvalidMessageException if prefix is invalid or the message type is not supported
     * @since 1.0
     */
    Class<? extends T> readMessageType(ByteBufferView buffer);
}
