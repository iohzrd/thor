package io.crypto;

import org.jetbrains.annotations.NotNull;

import crypto.pb.Crypto;

public abstract class PrivKey implements Key {
    @NotNull
    private final Crypto.KeyType keyType;

    public PrivKey(@NotNull Crypto.KeyType keyType) {
        super();
        this.keyType = keyType;
    }

    @NotNull
    public abstract byte[] sign(@NotNull byte[] var1);

    @NotNull
    public abstract PubKey publicKey();

    @NotNull
    public byte[] bytes() {
        return Key.marshalPrivateKey(this);
    }

    @NotNull
    public Crypto.KeyType getKeyType() {
        return this.keyType;
    }
}