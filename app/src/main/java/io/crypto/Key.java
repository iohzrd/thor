package io.crypto;

import com.google.protobuf.ByteString;

import crypto.pb.Crypto;

public interface Key {

    static byte[] marshalPublicKey(PubKey pubKey) {

        return Crypto.PublicKey.newBuilder().setType(pubKey.getKeyType()).
                setData(ByteString.copyFrom(pubKey.raw())).build().toByteArray();
    }


    static byte[] marshalPrivateKey(PrivKey privKey) {

        return Crypto.PrivateKey.newBuilder().setType(privKey.getKeyType()).
                setData(ByteString.copyFrom(privKey.raw())).build().toByteArray();
    }

    Crypto.KeyType getKeyType();


    byte[] bytes();

    byte[] raw();

}


