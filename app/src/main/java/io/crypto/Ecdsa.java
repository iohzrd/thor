package io.crypto;

import android.util.Pair;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.jetbrains.annotations.NotNull;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import crypto.pb.Crypto;
import kotlin.jvm.internal.Intrinsics;

public class Ecdsa {
    private static final ECNamedCurveParameterSpec CURVE;

    static {
        ECNamedCurveParameterSpec var10000 = ECNamedCurveTable.getParameterSpec("P-256");
        Intrinsics.checkNotNullExpressionValue(var10000, "ECNamedCurveTable.getParameterSpec(\"P-256\")");
        CURVE = var10000;
    }

    private static final Pair generateECDSAKeyPairWithCurve(ECNamedCurveParameterSpec curve, SecureRandom random) {
        try {
            KeyPairGenerator var3 = KeyPairGenerator.getInstance("ECDSA", new BouncyCastleProvider());
            boolean var4 = false;
            boolean var5 = false;
            boolean var7 = false;
            var3.initialize(curve, random);
            KeyPair var10000 = var3.genKeyPair();
            Intrinsics.checkNotNullExpressionValue(var10000, "with(\n        KeyPairGen…       genKeyPair()\n    }");
            KeyPair keypair = var10000;
            Pair var8;
            EcdsaPrivateKey var10002;
            PrivateKey var10004 = keypair.getPrivate();
            if (var10004 == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPrivateKey");
            } else {
                var10002 = new EcdsaPrivateKey((ECPrivateKey) var10004);
                EcdsaPublicKey var10003;
                PublicKey var10005 = keypair.getPublic();
                if (var10005 == null) {
                    throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
                } else {
                    var10003 = new EcdsaPublicKey((ECPublicKey) var10005);
                    var8 = new Pair(var10002, var10003);
                    return var8;
                }
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    // $FF: synthetic method
    static Pair generateECDSAKeyPairWithCurve$default(ECNamedCurveParameterSpec var0, SecureRandom var1, int var2, Object var3) {
        if ((var2 & 2) != 0) {
            var1 = new SecureRandom();
        }

        return generateECDSAKeyPairWithCurve(var0, var1);
    }


    @NotNull
    public static final Pair generateEcdsaKeyPair(@NotNull SecureRandom random) {
        Intrinsics.checkNotNullParameter(random, "random");
        return generateECDSAKeyPairWithCurve(CURVE, random);
    }

    // $FF: synthetic method
    public static Pair generateEcdsaKeyPair$default(SecureRandom var0, int var1, Object var2) {
        if ((var1 & 1) != 0) {
            var0 = new SecureRandom();
        }

        return generateEcdsaKeyPair(var0);
    }


    @NotNull
    public static final Pair generateEcdsaKeyPair() {
        return generateEcdsaKeyPair$default(null, 1, null);
    }

    @NotNull
    public static final Pair generateEcdsaKeyPair(@NotNull String curve) {
        Intrinsics.checkNotNullParameter(curve, "curve");
        ECNamedCurveParameterSpec var10000 = ECNamedCurveTable.getParameterSpec(curve);
        Intrinsics.checkNotNullExpressionValue(var10000, "ECNamedCurveTable.getParameterSpec(curve)");
        return generateECDSAKeyPairWithCurve$default(var10000, null, 2, null);
    }

    @NotNull
    public static final Pair ecdsaKeyPairFromKey(@NotNull EcdsaPrivateKey priv) {
        Intrinsics.checkNotNullParameter(priv, "priv");
        return new Pair(priv, priv.publicKey());
    }

    @NotNull
    public static final PrivKey unmarshalEcdsaPrivateKey(@NotNull byte[] keyBytes) {
        try {
            Intrinsics.checkNotNullParameter(keyBytes, "keyBytes");
            EcdsaPrivateKey var10000;
            PrivateKey var10002 = KeyFactory.getInstance("ECDSA", new BouncyCastleProvider()).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            if (var10002 == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPrivateKey");
            } else {
                var10000 = new EcdsaPrivateKey((ECPrivateKey) var10002);
                return var10000;
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @NotNull
    public static final EcdsaPublicKey unmarshalEcdsaPublicKey(@NotNull byte[] keyBytes) {
        try {
            Intrinsics.checkNotNullParameter(keyBytes, "keyBytes");
            KeyFactory var1 = KeyFactory.getInstance("ECDSA", new BouncyCastleProvider());
            boolean var2 = false;
            boolean var3 = false;
            boolean var5 = false;
            EcdsaPublicKey var10000;
            PublicKey var10002 = var1.generatePublic(new X509EncodedKeySpec(keyBytes));
            if (var10002 == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
            } else {
                var10000 = new EcdsaPublicKey((ECPublicKey) var10002);
                return var10000;
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @NotNull
    public static EcdsaPublicKey decodeEcdsaPublicKeyUncompressed(@NotNull String ecCurve, @NotNull byte[] keyBytes) {
        try {
            Intrinsics.checkNotNullParameter(ecCurve, "ecCurve");
            Intrinsics.checkNotNullParameter(keyBytes, "keyBytes");
            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(ecCurve);
            KeyFactory kf = KeyFactory.getInstance("ECDSA", new BouncyCastleProvider());
            Intrinsics.checkNotNullExpressionValue(spec, "spec");
            ECNamedCurveSpec params = new ECNamedCurveSpec(ecCurve, spec.getCurve(), spec.getG(), spec.getN());
            ECPoint point = ECPointUtil.decodePoint(params.getCurve(), keyBytes);
            ECPublicKeySpec pubKeySpec = null; // TODO BUG new ECPublicKeySpec(point, params);
            PublicKey publicKey = kf.generatePublic(pubKeySpec);
            if (publicKey == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
            } else {
                ECPublicKey var10000 = (ECPublicKey) publicKey;
                return new EcdsaPublicKey((ECPublicKey) publicKey);
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static final class EcdsaPublicKey extends PubKey {
        @NotNull
        private final ECPublicKey pub;

        public EcdsaPublicKey(@NotNull ECPublicKey pub) {
            super(Crypto.KeyType.ECDSA);
            this.pub = pub;
        }

        @NotNull
        public byte[] raw() {
            byte[] var10000 = this.pub.getEncoded();
            Intrinsics.checkNotNullExpressionValue(var10000, "pub.encoded");
            return var10000;
        }

        public boolean verify(@NotNull byte[] data, @NotNull byte[] signature) {
            try {
                Intrinsics.checkNotNullParameter(data, "data");
                Intrinsics.checkNotNullParameter(signature, "signature");
                Signature var3 = Signature.getInstance("SHA256withECDSA", new BouncyCastleProvider());
                boolean var4 = false;
                boolean var5 = false;
                boolean var7 = false;
                var3.initVerify(this.pub);
                var3.update(data);
                return var3.verify(signature);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public int hashCode() {
            return this.pub.hashCode();
        }

        @NotNull
        public final ECPublicKey getPub() {
            return this.pub;
        }
    }


    public static final class EcdsaPrivateKey extends PrivKey {
        @NotNull
        private final ECPrivateKey priv;

        public EcdsaPrivateKey(@NotNull ECPrivateKey priv) {
            super(Crypto.KeyType.ECDSA);
            this.priv = priv;
            if (Intrinsics.areEqual(this.priv.getFormat(), "PKCS#8") ^ true) {
                throw new RuntimeException("Private key must be of \"PKCS#8\" format");
            }
        }

        @NotNull
        public byte[] raw() {
            byte[] var10000 = this.priv.getEncoded();
            Intrinsics.checkNotNullExpressionValue(var10000, "priv.encoded");
            return var10000;
        }

        @NotNull
        public byte[] sign(@NotNull byte[] data) {
            try {
                Intrinsics.checkNotNullParameter(data, "data");
                Signature var2 = Signature.getInstance("SHA256withECDSA", new BouncyCastleProvider());
                boolean var3 = false;
                boolean var4 = false;
                boolean var6 = false;
                var2.initSign(this.priv);
                var2.update(data);
                byte[] var10000 = var2.sign();
                Intrinsics.checkNotNullExpressionValue(var10000, "with(\n            Signat…         sign()\n        }");
                return var10000;
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        /*
        // $FF: synthetic method
        // $FF: bridge method
        public PubKey publicKey() {
            return (PubKey)this.publicKey();
        }*/

        @NotNull
        public EcdsaPublicKey publicKey() {

            ECPrivateKey var10000 = this.priv;
            if (var10000 == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey");
            } else {
                try {
                    BCECPrivateKey var2 = (BCECPrivateKey) var10000;
                    boolean var3 = false;
                    boolean var4 = false;
                    boolean var6 = false;
                    ECParameterSpec var9 = var2.getParameters();
                    Intrinsics.checkNotNullExpressionValue(var9, "parameters");
                    org.bouncycastle.math.ec.ECPoint var10 = var9.getG();
                    if (var2 == null) {
                        throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.jce.interfaces.ECPrivateKey");
                    } else {
                        org.bouncycastle.math.ec.ECPoint q = var10.multiply(((org.bouncycastle.jce.interfaces.ECPrivateKey) var2).getD());
                        ECPublicKeySpec pubSpec = new ECPublicKeySpec(q, var2.getParameters());
                        KeyFactory var8 = KeyFactory.getInstance("ECDSA", new BouncyCastleProvider());
                        var3 = false;
                        var4 = false;
                        var6 = false;
                        EcdsaPublicKey var11;
                        PublicKey var10002 = var8.generatePublic(pubSpec);
                        if (var10002 == null) {
                            throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
                        } else {
                            var11 = new EcdsaPublicKey((ECPublicKey) var10002);
                            return var11;
                        }
                    }
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
        }

        public int hashCode() {
            return this.priv.hashCode();
        }

        @NotNull
        public final ECPrivateKey getPriv() {
            return this.priv;
        }
    }

}
