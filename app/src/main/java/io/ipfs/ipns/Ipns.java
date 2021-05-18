package io.ipfs.ipns;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import crypto.pb.Crypto;
import io.ipfs.IPFS;
import io.ipfs.cid.Cid;
import io.ipfs.core.InvalidRecord;
import io.ipfs.core.Validator;
import io.ipfs.crypto.Ecdsa;
import io.ipfs.crypto.Ed25519;
import io.ipfs.crypto.PrivKey;
import io.ipfs.crypto.PubKey;
import io.ipfs.crypto.Rsa;
import io.ipfs.crypto.Secp256k1;
import io.ipfs.host.PeerId;
import io.ipfs.multihash.Multihash;
import ipns.pb.Ipns.IpnsEntry;


public class Ipns implements Validator {

    @NonNull
    private static PubKey ExtractPublicKey(@NonNull PeerId id) {

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
            return unmarshalPublicKey(data);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static PubKey unmarshalPublicKey(byte[] data) throws InvalidProtocolBufferException {

        Crypto.PublicKey pmes = Crypto.PublicKey.parseFrom(data);

        byte[] pubKeyData = pmes.getData().toByteArray();

        switch (pmes.getType()) {
            case RSA:
                return Rsa.unmarshalRsaPublicKey(pubKeyData);
            case ECDSA:
                return Ecdsa.unmarshalEcdsaPublicKey(pubKeyData);
            case Secp256k1:
                return Secp256k1.unmarshalSecp256k1PublicKey(pubKeyData);
            case Ed25519:
                return Ed25519.unmarshalEd25519PublicKey(pubKeyData);
            default:
                throw new RuntimeException("BadKeyTypeException");
        }
    }

    @SuppressLint("SimpleDateFormat")
    @NonNull
    public static Date getDate(@NonNull String format) throws ParseException {
        return Objects.requireNonNull(new SimpleDateFormat(IPFS.TimeFormatIpfs).parse(format));
    }

    public static ipns.pb.Ipns.IpnsEntry Create(@NonNull PrivKey sk, @NonNull byte[] bytes,
                                                long sequence, @NonNull Date eol) {

        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(eol);
        ipns.pb.Ipns.IpnsEntry entry = ipns.pb.Ipns.IpnsEntry.newBuilder()
                .setValidityType(ipns.pb.Ipns.IpnsEntry.ValidityType.EOL)
                .setSequence(sequence)
                .setValue(ByteString.copyFrom(bytes))
                .setValidity(ByteString.copyFrom(format.getBytes())).buildPartial();
        byte[] sig = sk.sign(ipnsEntryDataForSig(entry));

        return entry.toBuilder().setSignature(ByteString.copyFrom(sig)).build();
    }

    public static ipns.pb.Ipns.IpnsEntry EmbedPublicKey(@NonNull PubKey pk,
                                                        @NonNull ipns.pb.Ipns.IpnsEntry entry) {

        try {
            PeerId peerId = PeerId.fromPubKey(pk);
            ExtractPublicKey(peerId);
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
    public Entry validate(@NonNull byte[] key, byte[] value) throws InvalidRecord {

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        int index = Bytes.indexOf(key, ipns);
        if (index != 0) {
            throw new InvalidRecord();
        }

        ipns.pb.Ipns.IpnsEntry entry;
        try {
            entry = IpnsEntry.parseFrom(value);
            Objects.requireNonNull(entry);
        } catch (Throwable throwable) {
            throw new InvalidRecord();
        }

        byte[] pid = Arrays.copyOfRange(key, ipns.length, key.length);
        PeerId peerId;
        try {
            Multihash mh = Multihash.deserialize(pid);
            peerId = PeerId.fromBase58(mh.toBase58());
        } catch (Throwable throwable) {
            throw new InvalidRecord();
        }

        PubKey pubKey;
        try {
            pubKey = getPublicKey(peerId, entry);
            Objects.requireNonNull(pubKey);
        } catch (Throwable throwable) {
            throw new InvalidRecord();
        }
        Validate(pubKey, entry);


        return new Entry(peerId, GetEOL(entry), entry.getValue().toStringUtf8(), entry.getSequence());
    }

    private int Compare(@NonNull Ipns.Entry a, @NonNull Ipns.Entry b) {

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

    @Override
    public int compare(@NonNull Ipns.Entry rec, @NonNull Ipns.Entry cmp) {
        return Compare(rec, cmp);
    }

    @NonNull
    private Date GetEOL(@NonNull ipns.pb.Ipns.IpnsEntry entry) throws InvalidRecord {
        try {
            if (entry.getValidityType() != ipns.pb.Ipns.IpnsEntry.ValidityType.EOL) {
                throw new InvalidRecord();
            }
            String date = new String(entry.getValidity().toByteArray());
            return getDate(date);
        } catch (Throwable throwable) {
            throw new InvalidRecord();
        }
    }

    // ExtractPublicKey extracts a public key matching `pid` from the IPNS record,
    // if possible.
    //
    // This function returns (nil, nil) when no public key can be extracted and
    // nothing is malformed.
    @NonNull
    private PubKey ExtractPublicKey(@NonNull PeerId pid, @NonNull ipns.pb.Ipns.IpnsEntry entry)
            throws InvalidRecord, IOException {


        if (entry.hasPubKey()) {
            byte[] pubKey = entry.getPubKey().toByteArray();

            PubKey pk = unmarshalPublicKey(pubKey);

            PeerId expPid = PeerId.fromPubKey(pk);

            if (!Objects.equals(pid, expPid)) {
                throw new InvalidRecord();
            }
            return pk;
        }

        return ExtractPublicKey(pid);
    }

    @NonNull
    private PubKey getPublicKey(@NonNull PeerId pid, @NonNull ipns.pb.Ipns.IpnsEntry entry)
            throws IOException, InvalidRecord {
        return ExtractPublicKey(pid, entry);
    }

    private void Validate(@NonNull PubKey pk, @NonNull ipns.pb.Ipns.IpnsEntry entry) throws InvalidRecord {

        if (!pk.verify(Ipns.ipnsEntryDataForSig(entry), entry.getSignature().toByteArray())) {
            throw new InvalidRecord();
        }

        try {
            Date eol = GetEOL(entry);

            if (new Date().after(eol)) {
                throw new InvalidRecord();
            }
        } catch (Throwable throwable) {
            throw new InvalidRecord();
        }
    }

    public static class Entry {
        private final PeerId peerId;
        private final long sequence;
        private final String value;
        private final Date eol;

        public Entry(@NonNull PeerId peerId, @NonNull Date eol,
                     @NonNull String value, long sequence) {
            this.peerId = peerId;
            this.eol = eol;
            this.sequence = sequence;
            this.value = value;
        }

        public Date getEol() {
            return eol;
        }

        public PeerId getPeerId() {
            return peerId;
        }

        public long getSequence() {
            return sequence;
        }

        public String getValue() {
            return value;
        }
    }
}
