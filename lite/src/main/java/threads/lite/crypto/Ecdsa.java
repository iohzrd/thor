package threads.lite.crypto;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import crypto.pb.Crypto;


@SuppressWarnings("unused")
public class Ecdsa {
    private static final ECNamedCurveParameterSpec CURVE;

    static {
        CURVE = ECNamedCurveTable.getParameterSpec("P-256");
    }

    private static Pair<EcdsaPrivateKey, EcdsaPublicKey> generateECDSAKeyPairWithCurve(SecureRandom random) {
        try {
            KeyPairGenerator var3 = KeyPairGenerator.getInstance("ECDSA",
                    BouncyCastleProvider.PROVIDER_NAME);

            var3.initialize(Ecdsa.CURVE, random);

            KeyPair keypair = var3.genKeyPair();


            PrivateKey privateKey = keypair.getPrivate();
            if (privateKey == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPrivateKey");
            } else {
                EcdsaPrivateKey ecdsaPrivateKey =
                        new EcdsaPrivateKey((ECPrivateKey) privateKey);

                PublicKey keypairPublic = keypair.getPublic();
                if (keypairPublic == null) {
                    throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
                } else {

                    return Pair.create(ecdsaPrivateKey, new EcdsaPublicKey((ECPublicKey) keypairPublic));

                }
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    public static Pair<EcdsaPrivateKey, EcdsaPublicKey> generateEcdsaKeyPair(SecureRandom random) {

        return generateECDSAKeyPairWithCurve(random);
    }


    public static Pair<EcdsaPrivateKey, EcdsaPublicKey> ecdsaKeyPairFromKey(EcdsaPrivateKey priv) {

        return Pair.create(priv, priv.publicKey());
    }

    public static PrivKey unmarshalEcdsaPrivateKey(byte[] keyBytes) {
        try {

            EcdsaPrivateKey var10000;
            PrivateKey ecdsa = KeyFactory.getInstance("ECDSA",
                    BouncyCastleProvider.PROVIDER_NAME).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            if (ecdsa == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPrivateKey");
            } else {
                var10000 = new EcdsaPrivateKey((ECPrivateKey) ecdsa);
                return var10000;
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static EcdsaPublicKey unmarshalEcdsaPublicKey(byte[] keyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);

            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
            if (publicKey == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
            } else {
                return new EcdsaPublicKey((ECPublicKey) publicKey);
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
    /*
    public static EcdsaPublicKey decodeEcdsaPublicKeyUncompressed(String ecCurve, byte[] keyBytes) {
        try {

            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(ecCurve);
            KeyFactory kf = KeyFactory.getInstance("ECDSA", new BouncyCastleProvider());

            ECNamedCurveSpec params = new ECNamedCurveSpec(ecCurve, spec.getCurve(), spec.getG(), spec.getN());
            ECPoint point = ECPointUtil.decodePoint(params.getCurve(), keyBytes);
            ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, params);
            PublicKey publicKey = kf.generatePublic(pubKeySpec);
            if (publicKey == null) {
                throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
            } else {
                return new EcdsaPublicKey((ECPublicKey) publicKey);
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }*/

    public static final class EcdsaPublicKey extends PubKey {

        private final ECPublicKey publicKey;

        public EcdsaPublicKey(ECPublicKey pub) {
            super(Crypto.KeyType.ECDSA);
            this.publicKey = pub;
        }


        @NonNull
        public byte[] raw() {
            return this.publicKey.getEncoded();
        }

        public boolean verify(byte[] data, byte[] signature) {
            try {
                Signature sha256withECDSA = Signature.getInstance(
                        "SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
                sha256withECDSA.initVerify(this.publicKey);
                sha256withECDSA.update(data);
                return sha256withECDSA.verify(signature);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public int hashCode() {
            return this.publicKey.hashCode();
        }


        public final ECPublicKey getPub() {
            return this.publicKey;
        }
    }


    public static final class EcdsaPrivateKey extends PrivKey {

        private final ECPrivateKey priv;

        public EcdsaPrivateKey(ECPrivateKey priv) {
            super(Crypto.KeyType.ECDSA);
            this.priv = priv;
            if (!Objects.equals(this.priv.getFormat(), "PKCS#8")) {
                throw new RuntimeException("Private key must be of \"PKCS#8\" format");
            }
        }


        @NonNull
        public byte[] raw() {
            return this.priv.getEncoded();
        }


        public byte[] sign(byte[] data) {
            try {
                Signature signature = Signature.getInstance(
                        "SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
                signature.initSign(this.priv);
                signature.update(data);
                return signature.sign();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public EcdsaPublicKey publicKey() {

            ECPrivateKey priv = this.priv;
            if (priv == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey");
            } else {
                try {
                    BCECPrivateKey bcecPrivateKey = (BCECPrivateKey) priv;

                    ECParameterSpec parameters = bcecPrivateKey.getParameters();

                    org.bouncycastle.math.ec.ECPoint var10 = parameters.getG();
                    org.bouncycastle.math.ec.ECPoint q = var10.multiply(((org.bouncycastle.jce.interfaces.ECPrivateKey) bcecPrivateKey).getD());
                    ECPublicKeySpec pubSpec = new ECPublicKeySpec(q, bcecPrivateKey.getParameters());
                    KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);

                    PublicKey publicKey = keyFactory.generatePublic(pubSpec);
                    if (publicKey == null) {
                        throw new NullPointerException("null cannot be cast to non-null type java.security.interfaces.ECPublicKey");
                    } else {
                        return new EcdsaPublicKey((ECPublicKey) publicKey);
                    }
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            }
        }

        public int hashCode() {
            return this.priv.hashCode();
        }

        public final ECPrivateKey getPriv() {
            return this.priv;
        }
    }

}
