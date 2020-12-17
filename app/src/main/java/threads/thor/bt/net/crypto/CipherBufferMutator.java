package threads.thor.bt.net.crypto;

import java.nio.ByteBuffer;

import javax.crypto.Cipher;

import threads.thor.bt.net.buffer.BufferMutator;

public class CipherBufferMutator implements BufferMutator {

    private final Cipher cipher;

    public CipherBufferMutator(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public void mutate(ByteBuffer buffer) {
        if (buffer.hasRemaining()) {
            int position = buffer.position();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            buffer.position(position);
            try {
                bytes = cipher.update(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            buffer.put(bytes);
        }
    }
}
