package threads.thor.bt.kad;


import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import threads.thor.bt.kad.messages.PutRequest;

import static threads.thor.bt.bencode.Utils.buf2ary;

public class GenericStorage {
    private static final long EXPIRATION_INTERVAL_SECONDS = 2 * 60 * 60;
    private final ConcurrentHashMap<Key, StorageItem> items = new ConcurrentHashMap<>();


    public static Key fingerprint(byte[] pubkey, byte[] salt, ByteBuffer buf) {
        MessageDigest dig = ThreadLocalUtils.getThreadLocalSHA1();

        dig.reset();

        if (pubkey != null) {
            dig.update(pubkey);
            if (salt != null)
                dig.update(salt);
            return new Key(dig.digest());
        }
        dig.update(buf.duplicate());
        return new Key(dig.digest());

    }

    UpdateResult putOrUpdate(Key k, StorageItem newItem, long expected) {

        if (newItem.mutable())
            return UpdateResult.SIG_FAIL;

        while (true) {
            StorageItem oldItem = items.putIfAbsent(k, newItem);

            if (oldItem == null)
                return UpdateResult.SUCCESS;

            if (oldItem.mutable()) {
                if (!newItem.mutable())
                    return UpdateResult.IMMUTABLE_SUBSTITUTION_FAIL;
                if (newItem.sequenceNumber < oldItem.sequenceNumber)
                    return UpdateResult.SEQ_FAIL;
                if (expected >= 0 && oldItem.sequenceNumber >= 0 && oldItem.sequenceNumber != expected)
                    return UpdateResult.CAS_FAIL;
            }

            if (items.replace(k, oldItem, newItem))
                break;
        }

        return UpdateResult.SUCCESS;
    }

    public Optional<StorageItem> get(Key k) {
        return Optional.ofNullable(items.get(k));
    }

    void cleanup() {
        long now = System.currentTimeMillis();

        items.entrySet().removeIf(entry -> entry.getValue().expirationDate < now);
    }


    enum UpdateResult {
        SUCCESS,
        IMMUTABLE_SUBSTITUTION_FAIL,
        SIG_FAIL,
        CAS_FAIL,
        SEQ_FAIL
    }

    static class StorageItem {

        final byte[] pubkey;

        final byte[] value;
        final long expirationDate;
        long sequenceNumber = -1;
        byte[] signature;


        StorageItem(PutRequest req) {
            expirationDate = System.currentTimeMillis() + EXPIRATION_INTERVAL_SECONDS * 1000;
            value = buf2ary(req.getValue());

            if (req.getPubkey() != null) {
                sequenceNumber = req.getSequenceNumber();
                signature = req.getSignature();
                pubkey = req.getPubkey();
            } else {
                pubkey = null;
            }
        }


        boolean mutable() {
            return pubkey != null;
        }


    }

}
