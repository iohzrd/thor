package io.crypto;

import org.bouncycastle.jcajce.provider.digest.SHA1;
import org.jetbrains.annotations.NotNull;

import kotlin.jvm.internal.Intrinsics;

public class Hash {
    @NotNull
    public static byte[] sha1(@NotNull byte[] data) {
        Intrinsics.checkNotNullParameter(data, "data");
        byte[] var10000 = (new SHA1.Digest()).digest(data);
        Intrinsics.checkNotNullExpressionValue(var10000, "SHA1.Digest().digest(data)");
        return var10000;
    }

    @NotNull
    public static byte[] sha256(@NotNull byte[] data) {
        Intrinsics.checkNotNullParameter(data, "data");
        byte[] var10000 = (new org.bouncycastle.jcajce.provider.digest.SHA256.Digest()).digest(data);
        Intrinsics.checkNotNullExpressionValue(var10000, "SHA256.Digest().digest(data)");
        return var10000;
    }

    @NotNull
    public static byte[] sha512(@NotNull byte[] data) {
        Intrinsics.checkNotNullParameter(data, "data");
        byte[] var10000 = (new org.bouncycastle.jcajce.provider.digest.SHA512.Digest()).digest(data);
        Intrinsics.checkNotNullExpressionValue(var10000, "SHA512.Digest().digest(data)");
        return var10000;
    }
}
