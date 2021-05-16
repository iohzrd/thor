package io.crypto;



import crypto.pb.Crypto;

public abstract class PubKey implements Key {

    private final Crypto.KeyType keyType;

    public PubKey(Crypto.KeyType keyType) {
        super();
        this.keyType = keyType;
    }

    public abstract boolean verify(byte[] var1, byte[] var2);

    public byte[] bytes() {
        return Key.marshalPublicKey(this);
    }

    public Crypto.KeyType getKeyType() {
        return this.keyType;
    }
}

