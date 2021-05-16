package io.crypto;

import android.util.Pair;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.SecureRandom;

import crypto.pb.Crypto;

public class Ed25519 {

    public static final Pair generateEd25519KeyPair(
            SecureRandom random) {

        Ed25519KeyPairGenerator var1 = new Ed25519KeyPairGenerator();
        boolean var2 = false;
        boolean var3 = false;
        boolean var5 = false;
        var1.init(new Ed25519KeyGenerationParameters(random));
        AsymmetricCipherKeyPair keypair = var1.generateKeyPair();

        AsymmetricKeyParameter var10000 = keypair.getPrivate();
        if (var10000 == null) {
            throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters");
        } else {
            Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) var10000;
            Pair var8;
            Ed25519PrivateKey var10002 = new Ed25519PrivateKey(privateKey);
            Ed25519PublicKey var10003;
            AsymmetricKeyParameter var10005 = keypair.getPublic();
            if (var10005 == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.crypto.params.Ed25519PublicKeyParameters");
            } else {
                var10003 = new Ed25519PublicKey(((Ed25519PublicKeyParameters) var10005));
                var8 = new Pair(var10002, var10003);
                return var8;
            }
        }
    }

    // $FF: synthetic method
    public static Pair generateEd25519KeyPair$default(SecureRandom var0, int var1, Object var2) {
        if ((var1 & 1) != 0) {
            var0 = new SecureRandom();
        }

        return generateEd25519KeyPair(var0);
    }


    public static final Pair generateEd25519KeyPair() {
        return generateEd25519KeyPair$default(null, 1, null);
    }


    public static final PrivKey unmarshalEd25519PrivateKey(byte[] keyBytes) {

        return new Ed25519PrivateKey(new Ed25519PrivateKeyParameters(keyBytes, 0));
    }


    public static final PubKey unmarshalEd25519PublicKey(byte[] keyBytes) {

        return new Ed25519PublicKey(new Ed25519PublicKeyParameters(keyBytes, 0));
    }

    public static final class Ed25519PrivateKey extends PrivKey {
        private final Ed25519PrivateKeyParameters priv;

        public Ed25519PrivateKey(Ed25519PrivateKeyParameters priv) {
            super(Crypto.KeyType.Ed25519);
            this.priv = priv;
        }


        public byte[] raw() {
            return this.priv.getEncoded();
        }


        public byte[] sign(byte[] data) {

            Ed25519Signer var2 = new Ed25519Signer();
            boolean var3 = false;
            boolean var4 = false;
            boolean var6 = false;
            var2.init(true, this.priv);
            var2.update(data, 0, data.length);
            byte[] var10000 = var2.generateSignature();

            return var10000;
        }


        public PubKey publicKey() {
            Ed25519PublicKeyParameters var10002 = this.priv.generatePublicKey();

            return new Ed25519PublicKey(var10002);
        }

        public int hashCode() {
            return this.priv.hashCode();
        }
    }

    public static final class Ed25519PublicKey extends PubKey {
        private final Ed25519PublicKeyParameters pub;

        public Ed25519PublicKey(Ed25519PublicKeyParameters pub) {
            super(Crypto.KeyType.Ed25519);
            this.pub = pub;
        }


        public byte[] raw() {
            byte[] var10000 = this.pub.getEncoded();

            return var10000;
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
