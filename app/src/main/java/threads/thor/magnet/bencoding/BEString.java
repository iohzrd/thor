package threads.thor.magnet.bencoding;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * BEncoded string.
 *
 * @since 1.0
 */
public class BEString implements BEObject<byte[]> {

    private final Object lock;
    private final byte[] content;
    private final BEEncoder encoder;
    private volatile String stringValue;

    /**
     * @param content Binary representation of this string, as read from source.
     *                It is also the value of this string, being a UTF-8 encoded byte array.
     * @since 1.0
     */
    public BEString(byte[] content) {
        this.content = content;
        this.encoder = BEEncoder.encoder();
        this.lock = new Object();
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public byte[] getValue() {
        return content;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        encoder.encode(this, out);
    }

    public String getValue(Charset charset) {
        return new String(content, charset);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof BEString)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return Arrays.equals(content, ((BEString) obj).getContent());
    }

    @NonNull
    @Override
    public String toString() {
        if (stringValue == null) {
            synchronized (lock) {
                if (stringValue == null) {
                    stringValue = new String(content, StandardCharsets.UTF_8);
                }
            }
        }
        return stringValue;
    }
}
