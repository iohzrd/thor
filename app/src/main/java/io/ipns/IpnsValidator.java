package io.ipns;

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import io.LogUtils;
import io.core.InvalidRecord;
import io.core.Validator;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.protos.ipns.IpnsProtos;

public class IpnsValidator implements Validator {
    private static final String TAG = IpnsValidator.class.getSimpleName();

    @Override
    public void Validate(@NonNull String key, byte[] value) throws InvalidRecord {
        LogUtils.error(TAG, "Validate Record " + key);

        if(!key.startsWith("/ipns/")){
            throw new InvalidRecord("Key does not start prefix /ipns/");
        }
        String pid = key.replaceFirst("/ipns/", "");

        IpnsProtos.IpnsEntry entry;
        try {
            entry = IpnsProtos.IpnsEntry.parseFrom(value);
            Objects.requireNonNull(entry);
        } catch (Throwable throwable){
            throw new InvalidRecord("Record not valid");
        }
        /*
        PeerId peerId;
        try {
            peerId = PeerId.fromBase58(pid); // TODO support others
        } catch (Throwable throwable){
            throw new InvalidRecord("PeerID not valid");
        }

        PubKey pubk;
        try {
            pubk = getPublicKey(peerId, entry);
        }  catch (Throwable throwable){
            throw new InvalidRecord("Public key not extractable");
        }

        Validate(pubk, entry);*/

    }

    @Override
    public int Select(String key, List<byte[]> values) {
        return 0;
    }


    private PubKey getPublicKey(@NonNull PeerId pid, @NonNull IpnsProtos.IpnsEntry entry) {

        /*
        switch pk, err := ExtractPublicKey(pid, entry); err {
            case peer.ErrNoPublicKey:
            case nil:
                return pk, nil
            default:
                return nil, err
        }

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
        return null;
    }


    // Validates validates the given IPNS entry against the given public key.
    private void Validate(@NonNull PubKey pk, @NonNull IpnsProtos.IpnsEntry entry) throws InvalidRecord {
        // Check the ipns record signature with the public key

        if( !pk.verify(Ipns.ipnsEntryDataForSig(entry), entry.getSignature().toByteArray())){
            throw new InvalidRecord("ErrSignature"); // todo maybe better
        }

        try {
            Date eol = GetEOL(entry);

            if( new Date().after(eol) ) {
                throw new InvalidRecord("ErrExpiredRecord");
            }
        } catch (Throwable throwable){
            throw new InvalidRecord("" + throwable.getLocalizedMessage());
        }
        /*
        if ok, err := pk.Verify(
      , entry.GetSignature()); err != nil || !ok {
            return ErrSignature
        }

        eol, err := GetEOL(entry)
        if err != nil {
            return err
        }
        if time.Now().After(eol) {
            return ErrExpiredRecord
        }*/

    }


    // GetEOL returns the EOL of this IPNS entry
//
// This function returns ErrUnrecognizedValidity if the validity type of the
// record isn't EOL. Otherwise, it returns an error if it can't parse the EOL.
    Date GetEOL(@NonNull IpnsProtos.IpnsEntry entry) throws InvalidRecord, ParseException {
        if( entry.getValidityType() != IpnsProtos.IpnsEntry.ValidityType.EOL) {
            throw new InvalidRecord( "ErrUnrecognizedValidity"); // todo maybe better
        }
        return new SimpleDateFormat(Ipns.TimeFormatIpfs).parse(new String(entry.getValidity().toByteArray()));
    }

}
