package threads.thor.bt.protocol.crypto;

public enum EncryptionPolicy {

    /**
     * @since 1.2
     */
    REQUIRE_PLAINTEXT,

    /**
     * @since 1.2
     */
    PREFER_PLAINTEXT,

    /**
     * @since 1.2
     */
    PREFER_ENCRYPTED,

    /**
     * @since 1.2
     */
    REQUIRE_ENCRYPTED;

    public boolean isCompatible(EncryptionPolicy that) {
        if (this == REQUIRE_PLAINTEXT && that == REQUIRE_ENCRYPTED) {
            return false;
        } else return this != REQUIRE_ENCRYPTED || that != REQUIRE_PLAINTEXT;
    }
}
