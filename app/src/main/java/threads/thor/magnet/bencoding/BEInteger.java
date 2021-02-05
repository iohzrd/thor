package threads.thor.magnet.bencoding;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

/**
 * BEncoded integer.
 *
 * <p>«BEP-3: The BitTorrent Protocol Specification» defines integers
 * as unsigned numeric values with an arbitrary number of digits.
 *
 * @since 1.0
 */
public class BEInteger implements BEObject<BigInteger> {

    private final byte[] content;
    private final BigInteger value;
    private final BEEncoder encoder;

    /**
     * @param content Binary representation of this integer, as read from source.
     * @param value   Parsed value
     * @since 1.0
     */
    public BEInteger(byte[] content, BigInteger value) {
        this.content = content;
        this.value = value;
        encoder = BEEncoder.encoder();
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public BigInteger getValue() {
        return value;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        encoder.encode(this, out);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof BEInteger)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return value.equals(((BEInteger) obj).getValue());
    }

    @NonNull
    @Override
    public String toString() {
        return value.toString(10);
    }
}
