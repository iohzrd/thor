package threads.lite.crypto;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.SecureRandom;

import crypto.pb.Crypto;

@SuppressWarnings("unused")
public class Ed25519 {

    public static Pair<Ed25519PrivateKey, Ed25519PublicKey> generateEd25519KeyPair(
            SecureRandom random) {

        Ed25519KeyPairGenerator keyPairGenerator = new Ed25519KeyPairGenerator();
        keyPairGenerator.init(new Ed25519KeyGenerationParameters(random));
        AsymmetricCipherKeyPair keypair = keyPairGenerator.generateKeyPair();

        AsymmetricKeyParameter aPrivate = keypair.getPrivate();
        if (aPrivate == null) {
            throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters");
        } else {
            Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) aPrivate;
            Ed25519PrivateKey var10002 = new Ed25519PrivateKey(privateKey);

            AsymmetricKeyParameter keypairPublic = keypair.getPublic();
            if (keypairPublic == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.crypto.params.Ed25519PublicKeyParameters");
            } else {
                Ed25519PublicKey publicKey = new Ed25519PublicKey(((Ed25519PublicKeyParameters) keypairPublic));
                return Pair.create(var10002, publicKey);

            }
        }
    }

    public static PrivKey unmarshalEd25519PrivateKey(byte[] keyBytes) {

        return new Ed25519PrivateKey(new Ed25519PrivateKeyParameters(keyBytes, 0));
    }


    public static PubKey unmarshalEd25519PublicKey(byte[] keyBytes) {

        return new Ed25519PublicKey(new Ed25519PublicKeyParameters(keyBytes, 0));
    }

    public static final class Ed25519PrivateKey extends PrivKey {
        private final Ed25519PrivateKeyParameters privateKeyParameters;

        public Ed25519PrivateKey(Ed25519PrivateKeyParameters priv) {
            super(Crypto.KeyType.Ed25519);
            this.privateKeyParameters = priv;
        }


        @NonNull
        public byte[] raw() {
            return this.privateKeyParameters.getEncoded();
        }


        public byte[] sign(byte[] data) {
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, this.privateKeyParameters);
            signer.update(data, 0, data.length);

            return signer.generateSignature();
        }


        public PubKey publicKey() {
            Ed25519PublicKeyParameters var10002 = this.privateKeyParameters.generatePublicKey();

            return new Ed25519PublicKey(var10002);
        }

        public int hashCode() {
            return this.privateKeyParameters.hashCode();
        }
    }

    @SuppressWarnings("unused")
    public static final class Ed25519PublicKey extends PubKey {
        private final Ed25519PublicKeyParameters pub;

        public Ed25519PublicKey(Ed25519PublicKeyParameters pub) {
            super(Crypto.KeyType.Ed25519);
            this.pub = pub;
        }


        @NonNull
        public byte[] raw() {
            return this.pub.getEncoded();
        }

        public boolean verify(byte[] data, byte[] signature) {

            Ed25519Signer var3 = new Ed25519Signer();
            var3.init(false, this.pub);
            var3.update(data, 0, data.length);
            return var3.verifySignature(signature);
        }

        public int hashCode() {
            return this.pub.hashCode();
        }
    }

}
