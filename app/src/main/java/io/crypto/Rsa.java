package io.crypto;

import android.util.Pair;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import crypto.pb.Crypto;

public class Rsa {


    public static Pair generateRsaKeyPair(int bits, SecureRandom random) throws NoSuchAlgorithmException, IOException {

        if (bits < 2048) {
            throw new RuntimeException("rsa keys must be >= 512 bits to be useful");
        } else {
            KeyPairGenerator var3 = KeyPairGenerator.getInstance("RSA", new BouncyCastleProvider());
            boolean var4 = false;
            boolean var5 = false;
            boolean var7 = false;
            var3.initialize(bits, random);
            KeyPair var10000 = var3.genKeyPair();

            KeyPair kp = var10000;
            PrivateKey var10004 = kp.getPrivate();

            PublicKey var10005 = kp.getPublic();

            RsaPrivateKey var10002 = new RsaPrivateKey(var10004, var10005);
            var10005 = kp.getPublic();

            return new Pair(var10002, new RsaPublicKey(var10005));
        }
    }

    // $FF: synthetic method
    public static Pair generateRsaKeyPair$default(int var0, SecureRandom var1, int var2, Object var3) throws IOException, NoSuchAlgorithmException {
        if ((var2 & 2) != 0) {
            var1 = new SecureRandom();
        }

        return generateRsaKeyPair(var0, var1);
    }


    public static final Pair generateRsaKeyPair(int bits) throws IOException, NoSuchAlgorithmException {
        return generateRsaKeyPair$default(bits, null, 2, null);
    }


    public static final PubKey unmarshalRsaPublicKey(byte[] keyBytes) {
        try {
            PublicKey var10002 = KeyFactory.getInstance("RSA", new BouncyCastleProvider()).generatePublic(new X509EncodedKeySpec(keyBytes));

            return new RsaPublicKey(var10002);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    public static final PrivKey unmarshalRsaPrivateKey(byte[] keyBytes) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        RSAPrivateKey rsaPrivateKey = RSAPrivateKey.getInstance(ASN1Primitive.fromByteArray(keyBytes));

        RSAPrivateCrtKeyParameters privateKeyParameters = new RSAPrivateCrtKeyParameters(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent(), rsaPrivateKey.getPrivateExponent(), rsaPrivateKey.getPrime1(), rsaPrivateKey.getPrime2(), rsaPrivateKey.getExponent1(), rsaPrivateKey.getExponent2(), rsaPrivateKey.getCoefficient());
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameters);

        AlgorithmIdentifier var10000 = privateKeyInfo.getPrivateKeyAlgorithm();

        ASN1ObjectIdentifier var10 = var10000.getAlgorithm();

        String algorithmId = var10.getId();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded());
        PrivateKey sk = KeyFactory.getInstance(algorithmId, new BouncyCastleProvider()).generatePrivate(spec);
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKeyParameters.getModulus(), privateKeyParameters.getPublicExponent());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey pk = keyFactory.generatePublic(publicKeySpec);

        return new RsaPrivateKey(sk, pk);
    }


    public static final class RsaPrivateKey extends PrivKey {
        private final RsaPublicKey rsaPublicKey;
        private final byte[] pkcs1PrivateKeyBytes;
        private final PrivateKey sk;
        private final PublicKey pk;

        public RsaPrivateKey(PrivateKey sk, PublicKey pk) throws IOException {
            super(Crypto.KeyType.RSA);
            this.sk = sk;
            this.pk = pk;
            this.rsaPublicKey = new RsaPublicKey(this.pk);
            String var10000 = this.sk.getFormat();
            boolean isKeyOfFormat = var10000 != null && var10000.equals("PKCS#8");
            if (!isKeyOfFormat) {
                throw new RuntimeException("Private key must be of \"PKCS#8\" format");
            } else {
                PrivateKeyInfo bcPrivateKeyInfo = PrivateKeyInfo.getInstance(this.sk.getEncoded());
                ASN1Primitive var10001 = bcPrivateKeyInfo.parsePrivateKey().toASN1Primitive();

                byte[] var5 = var10001.getEncoded();

                this.pkcs1PrivateKeyBytes = var5;
            }
        }


        public byte[] raw() {
            return this.pkcs1PrivateKeyBytes;
        }


        public byte[] sign(byte[] data) {
            try {

                Signature var2 = Signature.getInstance("SHA256withRSA", new BouncyCastleProvider());
                boolean var3 = false;
                boolean var4 = false;
                boolean var6 = false;
                var2.initSign(this.sk);
                var2.update(data);
                byte[] var10000 = var2.sign();

                return var10000;
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }


        public PubKey publicKey() {
            return this.rsaPublicKey;
        }

        public int hashCode() {
            return this.pk.hashCode();
        }
    }


    public static final class RsaPublicKey extends PubKey {
        private final PublicKey k;

        public RsaPublicKey(PublicKey k) {
            super(Crypto.KeyType.RSA);
            this.k = k;
        }


        public byte[] raw() {
            byte[] var10000 = this.k.getEncoded();

            return var10000;
        }

        public boolean verify(byte[] data, byte[] signature) {
            try {

                Signature var3 = Signature.getInstance("SHA256withRSA", new BouncyCastleProvider());
                boolean var4 = false;
                boolean var5 = false;
                boolean var7 = false;
                var3.initVerify(this.k);
                var3.update(data);
                return var3.verify(signature);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public int hashCode() {
            return this.k.hashCode();
        }
    }
}
