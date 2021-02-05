package threads.thor.magnet.kad;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import threads.thor.magnet.bencode.BDecoder;

public class ThreadLocalUtils {

    private static final ThreadLocal<Random> randTL = ThreadLocal.withInitial(() -> {
        try {
            return SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            return new SecureRandom();
        }
    });

    private static final ThreadLocal<MessageDigest> sha1TL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            throw new Error("expected SHA1 digest to be available", e);
        }
    });

    private static final ThreadLocal<BDecoder> decoder = ThreadLocal.withInitial(BDecoder::new);


    public static Random getThreadLocalRandom() {
        return randTL.get();
    }

    public static BDecoder getDecoder() {
        return decoder.get();
    }

    public static MessageDigest getThreadLocalSHA1() {
        return sha1TL.get();
    }

}
