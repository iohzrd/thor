package net.luminis.quic.crypto;

import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

public final class ChaCha20ParameterSpec implements AlgorithmParameterSpec {

    // The nonce length is defined by the spec as 96 bits (12 bytes) in length.
    private static final int NONCE_LENGTH = 12;

    private final byte[] nonce;
    private final int counter;

    /**
     * Constructs a parameter set for ChaCha20 from the given nonce
     * and counter.
     *
     * @param nonce a 12-byte nonce value
     * @param counter the initial counter value
     *
     * @throws NullPointerException if {@code nonce} is {@code null}
     * @throws IllegalArgumentException if {@code nonce} is not 12 bytes
     *      in length
     */
    public ChaCha20ParameterSpec(byte[] nonce, int counter) {
        this.counter = counter;

        Objects.requireNonNull(nonce, "Nonce must be non-null");
        this.nonce = nonce.clone();
        if (this.nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException(
                    "Nonce must be 12-bytes in length");
        }
    }

    /**
     * Returns the nonce value.
     *
     * @return the nonce value.  This method returns a new array each time
     * this method is called.
     */
    public byte[] getNonce() {
        return nonce.clone();
    }

    /**
     * Returns the configured counter value.
     *
     * @return the counter value
     */
    public int getCounter() {
        return counter;
    }
}
