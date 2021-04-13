package io.ipns;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.protos.ipns.IpnsProtos;

public class Ipns {


    // TimeFormatIpfs is the format ipfs uses to represent time in string form.
    public static final String TimeFormatIpfs = "2006-01-02'T'15:04:05.999999999Z07:00";




    public static IpnsProtos.IpnsEntry Create(@NonNull PrivKey sk, @NonNull byte[] bytes,
                                              long sequence, @NonNull Instant eol) {


        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                TimeFormatIpfs).format(new Date(eol.toEpochMilli()));
        IpnsProtos.IpnsEntry entry = IpnsProtos.IpnsEntry.newBuilder()
                .setValidityType(IpnsProtos.IpnsEntry.ValidityType.EOL)
                .setSequence(sequence)
                .setValue(ByteString.copyFrom(bytes))
                .setValidity(ByteString.copyFrom(format.getBytes())).buildPartial();
        byte[] sig = sk.sign(ipnsEntryDataForSig(entry));

        return entry.toBuilder().setSignature(ByteString.copyFrom(sig)).build();
    }

    public static IpnsProtos.IpnsEntry EmbedPublicKey(@NonNull PubKey pk, @NonNull IpnsProtos.IpnsEntry entry) {



        /* TODO maybe required
        PeerID id = PeerID.IDFromPublicKey(pk);
        if _, err := id.ExtractPublicKey(); err != peer.ErrNoPublicKey {
            // Either a *real* error or nil.
            return err
        }*/

        // We failed to extract the public key from the peer ID, embed it in the
        // record.
        byte[] pkBytes = pk.bytes();
        return entry.toBuilder().setPubKey(ByteString.copyFrom(pkBytes)).build();
    }




    public static byte[] ipnsEntryDataForSig(IpnsProtos.IpnsEntry e) {
        ByteString value = e.getValue();
        ByteString validity = e.getValidity();
        String type = e.getValidityType().toString();

        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(value.toByteArray());
            outputStream.write(validity.toByteArray());
            outputStream.write(type.getBytes());
            return outputStream.toByteArray();
        } catch (Throwable throwable){
            throw new RuntimeException( throwable);
        }
    }
}
