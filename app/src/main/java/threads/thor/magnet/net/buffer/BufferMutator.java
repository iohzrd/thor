package threads.thor.magnet.net.buffer;

import java.nio.ByteBuffer;

public interface BufferMutator {

    /**
     * Mutates the data between buffer's position and limit.
     * When this method has returned, buffer's position will be equal to its' limit.
     *
     * @since 1.6
     */
    void mutate(ByteBuffer buffer);
}
