package io.crypto;

import com.google.protobuf.ByteString;

import org.jetbrains.annotations.NotNull;

import crypto.pb.Crypto;
import kotlin.jvm.internal.Intrinsics;

public interface Key {
    @NotNull
    static byte[] marshalPublicKey(@NotNull PubKey pubKey) {
        Intrinsics.checkNotNullParameter(pubKey, "pubKey");
        byte[] var10000 = Crypto.PublicKey.newBuilder().setType(pubKey.getKeyType()).setData(ByteString.copyFrom(pubKey.raw())).build().toByteArray();
        Intrinsics.checkNotNullExpressionValue(var10000, "PbPublicKey.newBuilder()…           .toByteArray()");
        return var10000;
    }

    @NotNull
    static byte[] marshalPrivateKey(@NotNull PrivKey privKey) {
        Intrinsics.checkNotNullParameter(privKey, "privKey");
        byte[] var10000 = Crypto.PrivateKey.newBuilder().setType(privKey.getKeyType()).setData(ByteString.copyFrom(privKey.raw())).build().toByteArray();
        Intrinsics.checkNotNullExpressionValue(var10000, "PbPrivateKey.newBuilder(…           .toByteArray()");
        return var10000;
    }

    @NotNull
    Crypto.KeyType getKeyType();

    @NotNull
    byte[] bytes();

    @NotNull
    byte[] raw();

}


