package io.ipns;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.ipfs.IPFS;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.protos.ipns.IpnsProtos;

public class Ipns {


    public static IpnsProtos.IpnsEntry Create(@NonNull PrivKey sk, @NonNull byte[] bytes,
                                              long sequence, @NonNull Date eol) {


        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(eol);
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
