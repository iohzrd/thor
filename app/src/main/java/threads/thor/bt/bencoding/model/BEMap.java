package threads.thor.bt.bencoding.model;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

import threads.thor.bt.bencoding.BEEncoder;
import threads.thor.bt.bencoding.BEType;

/**
 * BEncoded dictionary.
 *
 * @since 1.0
 */
public class BEMap implements BEObject<Map<String, BEObject<?>>> {

    private final byte[] content;
    private final Map<String, BEObject<?>> value;
    private final BEEncoder encoder;

    /**
     * @param content Binary representation of this dictionary, as read from source.
     * @param value   Parsed value
     * @since 1.0
     */
    public BEMap(byte[] content, Map<String, BEObject<?>> value) {
        this.content = content;
        this.value = Collections.unmodifiableMap(value);
        encoder = BEEncoder.encoder();
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public Map<String, BEObject<?>> getValue() {
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

        if (!(obj instanceof BEMap)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        return value.equals(((BEMap) obj).getValue());
    }

    @NonNull
    @Override
    public String toString() {
        return value.toString();
    }
}
