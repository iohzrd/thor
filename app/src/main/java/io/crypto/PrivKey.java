package io.crypto;



import crypto.pb.Crypto;

public abstract class PrivKey implements Key {

    private final Crypto.KeyType keyType;

    public PrivKey(Crypto.KeyType keyType) {
        super();
        this.keyType = keyType;
    }

    public abstract byte[] sign(byte[] var1);


    public abstract PubKey publicKey();


    public byte[] bytes() {
        return Key.marshalPrivateKey(this);
    }


    public Crypto.KeyType getKeyType() {
        return this.keyType;
    }
}