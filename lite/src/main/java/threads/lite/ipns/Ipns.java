package threads.lite.ipns;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import crypto.pb.Crypto;
import ipns.pb.Ipns.IpnsEntry;
import threads.lite.IPFS;
import threads.lite.cid.Cid;
import threads.lite.cid.Multihash;
import threads.lite.cid.PeerId;
import threads.lite.core.RecordIssue;
import threads.lite.crypto.Key;
import threads.lite.crypto.PrivKey;
import threads.lite.crypto.PubKey;


public class Ipns implements Validator {

    @NonNull
    private static PubKey extractPublicKey(@NonNull PeerId id) {

        try (InputStream inputStream = new ByteArrayInputStream(id.getBytes())) {
            long version = Multihash.readVarint(inputStream);
            if (version != Cid.IDENTITY) {
                throw new Exception("not supported codec");
            }
            long length = Multihash.readVarint(inputStream);
            byte[] data = new byte[(int) length];
            int read = inputStream.read(data);
            if (read != length) {
                throw new RuntimeException("Key to short");
            }
            return Key.unmarshalPublicKey(data);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    @SuppressLint("SimpleDateFormat")
    @NonNull
    public static Date getDate(@NonNull String format) throws ParseException {
        return Objects.requireNonNull(new SimpleDateFormat(IPFS.TimeFormatIpfs).parse(format));
    }

    public static ipns.pb.Ipns.IpnsEntry create(@NonNull PrivKey sk, @NonNull byte[] bytes,
                                                long sequence, @NonNull Date eol,
                                                @NonNull Duration duration) {

        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(eol);
        ipns.pb.Ipns.IpnsEntry entry = ipns.pb.Ipns.IpnsEntry.newBuilder()
                .setValidityType(ipns.pb.Ipns.IpnsEntry.ValidityType.EOL)
                .setSequence(sequence)
                .setTtl(duration.toNanos())
                .setValue(ByteString.copyFrom(bytes))
                .setValidity(ByteString.copyFrom(format.getBytes())).buildPartial();
        byte[] sig = sk.sign(ipnsEntryDataForSig(entry));

        return entry.toBuilder().setSignature(ByteString.copyFrom(sig)).build();
    }

    public static ipns.pb.Ipns.IpnsEntry embedPublicKey(@NonNull PubKey pk,
                                                        @NonNull ipns.pb.Ipns.IpnsEntry entry) {

        try {
            PeerId peerId = PeerId.fromPubKey(pk);
            extractPublicKey(peerId);
            return entry;
        } catch (Throwable throwable) {
            // We failed to extract the public key from the peer ID, embed it in the
            // record.
            byte[] pkBytes = pk.bytes();
            return entry.toBuilder().setPubKey(ByteString.copyFrom(pkBytes)).build();
        }
    }

    public static byte[] ipnsEntryDataForSig(ipns.pb.Ipns.IpnsEntry e) {
        ByteString value = e.getValue();
        ByteString validity = e.getValidity();
        String type = e.getValidityType().toString();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(value.toByteArray());
            outputStream.write(validity.toByteArray());
            outputStream.write(type.getBytes());
            return outputStream.toByteArray();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @NonNull
    @Override
    public Entry validate(@NonNull byte[] key, byte[] value) throws RecordIssue {

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        int index = Bytes.indexOf(key, ipns);
        if (index != 0) {
            throw new RecordIssue("parsing issue");
        }

        ipns.pb.Ipns.IpnsEntry entry;
        try {
            entry = IpnsEntry.parseFrom(value);
            Objects.requireNonNull(entry);
        } catch (Throwable throwable) {
            throw new RecordIssue("parsing issue");
        }

        byte[] pid = Arrays.copyOfRange(key, ipns.length, key.length);
        PeerId peerId;
        try {
            Multihash mh = Multihash.deserialize(pid);
            peerId = PeerId.fromBase58(mh.toBase58());
        } catch (Throwable throwable) {
            throw new RecordIssue("peer issue");
        }

        PubKey pubKey;
        try {
            pubKey = getPublicKey(peerId, entry);
            Objects.requireNonNull(pubKey);
        } catch (Throwable throwable) {
            throw new RecordIssue("pubkey issue");
        }
        validate(pubKey, entry);

        Crypto.KeyType keyType = pubKey.getKeyType();

        return new Entry(peerId, keyType, getEOL(entry),
                entry.getValue().toStringUtf8(), entry.getSequence());
    }

    @Override
    public int compare(@NonNull Ipns.Entry a, @NonNull Ipns.Entry b) {

        long as = a.getSequence();
        long bs = b.getSequence();


        if (as > bs) {
            return 1;
        } else if (as < bs) {
            return -1;
        }

        Date at = a.getEol();
        Date bt = b.getEol();

        if (at.after(bt)) {
            return 1;
        } else if (bt.after(at)) {
            return -1;
        }
        return 0;
    }

    @NonNull
    private Date getEOL(@NonNull ipns.pb.Ipns.IpnsEntry entry) throws RecordIssue {
        try {
            if (entry.getValidityType() != ipns.pb.Ipns.IpnsEntry.ValidityType.EOL) {
                throw new RecordIssue("validity type");
            }
            String date = new String(entry.getValidity().toByteArray());
            return getDate(date);
        } catch (Throwable throwable) {
            throw new RecordIssue("data issue");
        }
    }

    // ExtractPublicKey extracts a public key matching `pid` from the IPNS record,
    // if possible.
    //
    // This function returns (nil, nil) when no public key can be extracted and
    // nothing is malformed.
    @NonNull
    private PubKey extractPublicKey(@NonNull PeerId pid, @NonNull ipns.pb.Ipns.IpnsEntry entry)
            throws RecordIssue, IOException {


        if (entry.hasPubKey()) {
            byte[] pubKey = entry.getPubKey().toByteArray();

            PubKey pk = Key.unmarshalPublicKey(pubKey);

            PeerId expPid = PeerId.fromPubKey(pk);

            if (!Objects.equals(pid, expPid)) {
                throw new RecordIssue("invalid peer");
            }
            return pk;
        }

        return extractPublicKey(pid);
    }

    @NonNull
    private PubKey getPublicKey(@NonNull PeerId pid, @NonNull ipns.pb.Ipns.IpnsEntry entry)
            throws IOException, RecordIssue {
        return extractPublicKey(pid, entry);
    }

    private void validate(@NonNull PubKey pk, @NonNull ipns.pb.Ipns.IpnsEntry entry) throws RecordIssue {

        if (!pk.verify(Ipns.ipnsEntryDataForSig(entry), entry.getSignature().toByteArray())) {
            throw new RecordIssue("signature wrong");
        }


        Date eol = getEOL(entry);

        if (new Date().after(eol)) {
            throw new RecordIssue("outdated");
        }

    }

    public static class Entry {
        @NonNull
        private final PeerId peerId;
        @NonNull
        private final Crypto.KeyType keyType;
        private final long sequence;
        @NonNull
        private final String value;
        @NonNull
        private final Date eol;

        public Entry(@NonNull PeerId peerId, @NonNull Crypto.KeyType keyType, @NonNull Date eol,
                     @NonNull String value, long sequence) {
            this.peerId = peerId;
            this.keyType = keyType;
            this.eol = eol;
            this.sequence = sequence;
            this.value = value;
        }

        @NonNull
        public Crypto.KeyType getKeyType() {
            return keyType;
        }

        @NonNull
        @Override
        public String toString() {
            return "Entry{" +
                    "peerId=" + peerId +
                    ", keyType=" + keyType +
                    ", sequence=" + sequence +
                    ", value='" + value + '\'' +
                    ", eol=" + eol +
                    '}';
        }

        @NonNull
        public Date getEol() {
            return eol;
        }

        @NonNull
        public PeerId getPeerId() {
            return peerId;
        }

        public long getSequence() {
            return sequence;
        }

        @NonNull
        public String getValue() {
            return value;
        }

        @NonNull
        public String getHash() {
            return value.replaceFirst(IPFS.IPFS_PATH, "");
        }
    }
}
