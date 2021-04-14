package io.ipns;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.common.primitives.Bytes;
import com.google.protobuf.InvalidProtocolBufferException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import crypto.pb.Crypto;
import io.LogUtils;
import io.core.InvalidRecord;
import io.core.Validator;
import io.ipfs.IPFS;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.EcdsaKt;
import io.libp2p.crypto.keys.Ed25519Kt;
import io.libp2p.crypto.keys.RsaKt;
import io.libp2p.crypto.keys.Secp256k1Kt;
import io.protos.ipns.IpnsProtos;

public class IpnsValidator implements Validator {

    @Override
    public void Validate(@NonNull byte[] key, byte[] value) throws InvalidRecord {

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        int index = Bytes.indexOf(key, ipns);
        if (index != 0) {
            throw new InvalidRecord("Key does not start prefix /ipns/");
        }

        IpnsProtos.IpnsEntry entry;
        try {
            entry = IpnsProtos.IpnsEntry.parseFrom(value);
            Objects.requireNonNull(entry);
        } catch (Throwable throwable) {
            throw new InvalidRecord("Record not valid");
        }

        byte[] pid = Arrays.copyOfRange(key, ipns.length, key.length);
        PeerId peerId;
        try {
            Multihash mh = Multihash.deserialize(pid);
            peerId = PeerId.fromBase58(mh.toBase58());
        } catch (Throwable throwable) {
            throw new InvalidRecord("PeerID not valid");
        }

        PubKey pubKey;
        try {
            pubKey = getPublicKey(peerId, entry);
            Objects.requireNonNull(pubKey);
        } catch (Throwable throwable) {
            throw new InvalidRecord("Public key not extractable");
        }
        Validate(pubKey, entry);

    }

    private int Compare(@NonNull IpnsProtos.IpnsEntry a, @NonNull IpnsProtos.IpnsEntry b) throws InvalidRecord, ParseException {

        long as = a.getSequence();
        long bs = b.getSequence();


        if (as > bs) {
            return 1;
        } else if (as < bs) {
            return -1;
        }

        Date at = GetEOL(a);
        Date bt = GetEOL(b);

        if (at.after(bt)) {
            return 1;
        } else if (bt.after(at)) {
            return -1;
        }
        return 0;
    }

    @Override
    public int Select(@NonNull byte[] rec, @NonNull byte[] cmp) {

        try {
            return Compare(IpnsProtos.IpnsEntry.parseFrom(rec),
                    IpnsProtos.IpnsEntry.parseFrom(cmp));

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    // ExtractPublicKey extracts a public key matching `pid` from the IPNS record,
    // if possible.
    //
    // This function returns (nil, nil) when no public key can be extracted and
    // nothing is malformed.
    @NonNull
    private PubKey ExtractPublicKey(@NonNull PeerId pid, @NonNull IpnsProtos.IpnsEntry entry)
            throws InvalidRecord, InvalidProtocolBufferException {
        if (entry.getPubKey() != null) {
            byte[] pubKey = entry.getPubKey().toByteArray();

            PubKey pk = unmarshalPublicKey(pubKey);

            PeerId expPid = PeerId.fromPubKey(pk);

            if (!Objects.equals(pid, expPid)) {
                throw new InvalidRecord("ErrPublicKeyMismatch");
            }
            return pk;
        }

        return ExtractPublicKey(pid);
    }


    // ExtractPublicKey attempts to extract the public key from an ID.
    //
    // This method returns ErrNoPublicKey if the peer ID looks valid but it can't extract
    // the public key.
    @NonNull
    PubKey ExtractPublicKey(@NonNull PeerId id) throws InvalidProtocolBufferException {



        /* TODO check if implemenation is correct
        decoded, err := mh.Decode([]byte(id))
        if err != nil {
            return nil, err
        }
        if decoded.Code != mh.IDENTITY {
            return nil, ErrNoPublicKey
        }
        pk, err := ic.UnmarshalPublicKey(decoded.Digest)
        if err != nil {
            return nil, err
        }
        return pk, nil *

         */
        return unmarshalPublicKey(id.getBytes());
    }

    PubKey unmarshalPublicKey(byte[] data) throws InvalidProtocolBufferException {

        Crypto.PublicKey pmes = Crypto.PublicKey.parseFrom(data);

        byte[] pmesd = pmes.getData().toByteArray();

        switch (pmes.getType()) {
            case RSA:
                return RsaKt.unmarshalRsaPublicKey(pmesd);
            case ECDSA:
                return EcdsaKt.unmarshalEcdsaPublicKey(pmesd);
            case Secp256k1:
                return Secp256k1Kt.unmarshalSecp256k1PublicKey(pmesd);
            case Ed25519:
                return Ed25519Kt.unmarshalEd25519PublicKey(pmesd);
            default:
                throw new RuntimeException("BadKeyTypeException");
        }
    }

    @NonNull
    private PubKey getPublicKey(@NonNull PeerId pid, @NonNull IpnsProtos.IpnsEntry entry)
            throws InvalidProtocolBufferException, InvalidRecord {

        return ExtractPublicKey(pid, entry);

        /* TODO maybe

        if v.KeyBook == nil {
            log.Debugf("public key with hash %s not found in IPNS record and no peer store provided", pid)
            return nil, ErrPublicKeyNotFound
        }

        pubk := v.KeyBook.PubKey(pid)
        if pubk == nil {
            log.Debugf("public key with hash %s not found in peer store", pid)
            return nil, ErrPublicKeyNotFound
        }
        return pubk, nil*/
    }


    private void Validate(@NonNull PubKey pk, @NonNull IpnsProtos.IpnsEntry entry) throws InvalidRecord {

        if( !pk.verify(Ipns.ipnsEntryDataForSig(entry), entry.getSignature().toByteArray())){
            throw new InvalidRecord("ErrSignature"); // todo maybe better
        }

        try {
            Date eol = GetEOL(entry);

            if (new Date().after(eol)) {
                throw new InvalidRecord("ErrExpiredRecord");
            }
        } catch (Throwable throwable) {
            throw new InvalidRecord("" + throwable.getLocalizedMessage());
        }
    }

    @NonNull
    private Date GetEOL(@NonNull IpnsProtos.IpnsEntry entry) throws InvalidRecord, ParseException {
        if (entry.getValidityType() != IpnsProtos.IpnsEntry.ValidityType.EOL) {
            throw new InvalidRecord("ErrUnrecognizedValidity"); // todo maybe better
        }
        String date = new String(entry.getValidity().toByteArray());
        return getDate(date);
    }
    @SuppressLint("SimpleDateFormat")
    @NonNull
    public static Date getDate(@NonNull String format) throws ParseException {
        return Objects.requireNonNull(new SimpleDateFormat(IPFS.TimeFormatIpfs).parse(format));
    }

}
