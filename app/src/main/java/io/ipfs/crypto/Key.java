package io.ipfs.crypto;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import crypto.pb.Crypto;

public interface Key {

    @NonNull
    static byte[] marshalPublicKey(@NonNull PubKey pubKey) {
        return Crypto.PublicKey.newBuilder().setType(pubKey.getKeyType()).
                setData(ByteString.copyFrom(pubKey.raw())).build().toByteArray();
    }


    @NonNull
    static byte[] marshalPrivateKey(@NonNull PrivKey privKey) {
        return Crypto.PrivateKey.newBuilder().setType(privKey.getKeyType()).
                setData(ByteString.copyFrom(privKey.raw())).build().toByteArray();
    }

    @NonNull
    Crypto.KeyType getKeyType();

    @NonNull
    byte[] bytes();

    @NonNull
    byte[] raw();

}


