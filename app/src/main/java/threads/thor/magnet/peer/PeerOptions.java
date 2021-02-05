package threads.thor.magnet.peer;

import threads.thor.magnet.protocol.crypto.EncryptionPolicy;

public class PeerOptions {

    private final EncryptionPolicy encryptionPolicy;

    private PeerOptions(EncryptionPolicy encryptionPolicy) {
        this.encryptionPolicy = encryptionPolicy;
    }

    /**
     * Returns default options.
     *
     * @return Options with all values set to their defaults.
     * @since 1.2
     */
    public static PeerOptions defaultOptions() {
        return new PeerOptions(EncryptionPolicy.PREFER_PLAINTEXT);
    }

    /**
     * @return Message Stream Encryption policy
     * @since 1.2
     */
    public EncryptionPolicy getEncryptionPolicy() {
        return encryptionPolicy;
    }

    /**
     * @return Copy of the original options with adjusted Message Stream Encryption policy
     * @since 1.2
     */
    public PeerOptions withEncryptionPolicy(EncryptionPolicy encryptionPolicy) {
        return new Builder().encryptionPolicy(encryptionPolicy).build();
    }

    /**
     * @since 1.2
     */
    private static class Builder {
        private EncryptionPolicy encryptionPolicy;

        private Builder() {
        }

        /**
         * Indicate policy regarding Message Stream Encryption.
         *
         * @since 1.2
         */
        Builder encryptionPolicy(EncryptionPolicy encryptionPolicy) {
            this.encryptionPolicy = encryptionPolicy;
            return this;
        }

        /**
         * @since 1.2
         */
        PeerOptions build() {
            return new PeerOptions(encryptionPolicy);
        }
    }
}
