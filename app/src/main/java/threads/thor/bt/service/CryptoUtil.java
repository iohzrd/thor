package threads.thor.bt.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import threads.thor.bt.BtException;

public class CryptoUtil {

    /**
     * Calculate SHA-1 digest of a byte array.
     *
     * @since 1.0
     */
    public static byte[] getSha1Digest(byte[] bytes) {
        MessageDigest crypto;
        try {
            crypto = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new BtException("Unexpected error", e);
        }
        return crypto.digest(bytes);
    }
}
