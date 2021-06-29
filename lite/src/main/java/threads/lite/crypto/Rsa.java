package threads.lite.crypto;

import android.util.Pair;

import androidx.annotation.NonNull;

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
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import crypto.pb.Crypto;

@SuppressWarnings("unused")
public class Rsa {


    public static Pair<RsaPrivateKey, RsaPublicKey> generateRsaKeyPair(int bits, SecureRandom random)
            throws NoSuchAlgorithmException, IOException, NoSuchProviderException {

        if (bits < 2048) {
            throw new RuntimeException("rsa keys must be >= 512 bits to be useful");
        } else {
            KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA",
                    BouncyCastleProvider.PROVIDER_NAME);
            rsa.initialize(bits, random);
            KeyPair kp = rsa.genKeyPair();
            RsaPrivateKey var10002 = new RsaPrivateKey(kp.getPrivate(), kp.getPublic());
            return Pair.create(var10002, new RsaPublicKey(kp.getPublic()));
        }
    }


    public static PubKey unmarshalRsaPublicKey(byte[] keyBytes) {
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA",
                    BouncyCastleProvider.PROVIDER_NAME).generatePublic(new X509EncodedKeySpec(keyBytes));
            return new RsaPublicKey(publicKey);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @NonNull
    public static PrivKey unmarshalRsaPrivateKey(byte[] keyBytes)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {

        RSAPrivateKey rsaPrivateKey = RSAPrivateKey.getInstance(ASN1Primitive.fromByteArray(keyBytes));

        RSAPrivateCrtKeyParameters privateKeyParameters = new RSAPrivateCrtKeyParameters(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent(), rsaPrivateKey.getPrivateExponent(), rsaPrivateKey.getPrime1(), rsaPrivateKey.getPrime2(), rsaPrivateKey.getExponent1(), rsaPrivateKey.getExponent2(), rsaPrivateKey.getCoefficient());
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameters);

        AlgorithmIdentifier privateKeyAlgorithm = privateKeyInfo.getPrivateKeyAlgorithm();

        ASN1ObjectIdentifier algorithm = privateKeyAlgorithm.getAlgorithm();

        String algorithmId = algorithm.getId();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded());
        PrivateKey sk = KeyFactory.getInstance(algorithmId, BouncyCastleProvider.PROVIDER_NAME).generatePrivate(spec);
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKeyParameters.getModulus(),
                privateKeyParameters.getPublicExponent());
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

                this.pkcs1PrivateKeyBytes = var10001.getEncoded();
            }
        }


        @NonNull
        public byte[] raw() {
            return this.pkcs1PrivateKeyBytes;
        }


        public byte[] sign(byte[] data) {
            try {

                Signature var2 = Signature.getInstance("SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);

                var2.initSign(this.sk);
                var2.update(data);

                return var2.sign();
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
        private final PublicKey publicKey;

        public RsaPublicKey(PublicKey publicKey) {
            super(Crypto.KeyType.RSA);
            this.publicKey = publicKey;
        }


        @NonNull
        public byte[] raw() {
            return this.publicKey.getEncoded();
        }

        public boolean verify(byte[] data, byte[] signature) {
            try {

                Signature sha256withRSA = Signature.getInstance(
                        "SHA256withRSA", BouncyCastleProvider.PROVIDER_NAME);
                sha256withRSA.initVerify(this.publicKey);
                sha256withRSA.update(data);
                return sha256withRSA.verify(signature);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public int hashCode() {
            return this.publicKey.hashCode();
        }
    }
}
