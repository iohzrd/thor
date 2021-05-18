package io.crypto;



import androidx.annotation.NonNull;

import crypto.pb.Crypto;

public abstract class PrivKey implements Key {

    private final Crypto.KeyType keyType;

    public PrivKey(Crypto.KeyType keyType) {
        super();
        this.keyType = keyType;
    }

    public abstract byte[] sign(byte[] data);


    public abstract PubKey publicKey();


    @NonNull
    public byte[] bytes() {
        return Key.marshalPrivateKey(this);
    }


    @NonNull
    public Crypto.KeyType getKeyType() {
        return this.keyType;
    }
}