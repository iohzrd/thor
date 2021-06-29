package threads.lite.crypto;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.math.ec.FixedPointUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;

import crypto.pb.Crypto;


@SuppressWarnings("unused")
public class Secp256k1 {

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE;
    private static final BigInteger S_UPPER_BOUND;
    private static final BigInteger S_FIXER_VALUE;

    static {
        FixedPointUtil.precompute(CURVE_PARAMS.getG());

        ECCurve var5 = CURVE_PARAMS.getCurve();

        ECPoint var6 = CURVE_PARAMS.getG();

        BigInteger var7 = CURVE_PARAMS.getN();

        CURVE = new ECDomainParameters(var5, var6, var7, CURVE_PARAMS.getH());
        S_UPPER_BOUND = new BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16);
        S_FIXER_VALUE = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    }

    public static Pair<Secp256k1PrivateKey, Secp256k1PublicKey> generateSecp256k1KeyPair(@NonNull SecureRandom random) {

        ECKeyPairGenerator ecKeyPairGenerator = new ECKeyPairGenerator();
        X9ECParameters secp256k1 = SECNamedCurves.getByName("secp256k1");


        ECDomainParameters domain = new ECDomainParameters(secp256k1.getCurve(),
                secp256k1.getG(), secp256k1.getN(), secp256k1.getH());
        ecKeyPairGenerator.init(new ECKeyGenerationParameters(domain, random));
        AsymmetricCipherKeyPair keypair = ecKeyPairGenerator.generateKeyPair();

        AsymmetricKeyParameter aPrivate = keypair.getPrivate();
        if (aPrivate == null) {
            throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.crypto.params.ECPrivateKeyParameters");
        } else {
            ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) aPrivate;

            Secp256k1PrivateKey secp256k1PrivateKey = new Secp256k1PrivateKey(privateKey);

            AsymmetricKeyParameter keypairPublic = keypair.getPublic();
            if (keypairPublic == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.crypto.params.ECPublicKeyParameters");
            } else {
                return Pair.create(secp256k1PrivateKey, new Secp256k1PublicKey((ECPublicKeyParameters) keypairPublic));
            }
        }
    }

    public static PrivKey unmarshalSecp256k1PrivateKey(byte[] data) {
        return new Secp256k1PrivateKey(new ECPrivateKeyParameters(new BigInteger(1, data), CURVE));
    }


    public static PubKey unmarshalSecp256k1PublicKey(byte[] data) {

        return new Secp256k1PublicKey(new ECPublicKeyParameters(CURVE.getCurve().decodePoint(data), CURVE));
    }

    public static PubKey secp256k1PublicKeyFromCoordinates(BigInteger x, BigInteger y) {

        return new Secp256k1PublicKey(new ECPublicKeyParameters(CURVE.getCurve().createPoint(x, y), CURVE));
    }


    public static BigInteger accessUpperBound() {
        return S_UPPER_BOUND;
    }


    public static BigInteger accessFixerValue() {
        return S_FIXER_VALUE;
    }

    public static ECDomainParameters accessCurve() {
        return CURVE;
    }

    public static final class Secp256k1PrivateKey extends PrivKey {
        private final BigInteger priv;
        private final ECPrivateKeyParameters privateKey;

        public Secp256k1PrivateKey(ECPrivateKeyParameters privateKey) {
            super(Crypto.KeyType.Secp256k1);
            this.privateKey = privateKey;
            this.priv = this.privateKey.getD();
        }


        @NonNull
        public byte[] raw() {

            return this.priv.toByteArray();
        }


        public byte[] sign(byte[] data) {
            try {

                ECDSASigner signer = new ECDSASigner();
                signer.init(true, new ParametersWithRandom(this.privateKey, new SecureRandom()));
                BigInteger[] var10 = signer.generateSignature(Hash.sha256(data));
                Pair<BigInteger, BigInteger> var4 = Pair.create(var10[0], var10[1]);
                BigInteger r = var4.first;
                BigInteger s = var4.second;
                BigInteger var10000;
                if (s.compareTo(Secp256k1.accessUpperBound()) <= 0) {
                    var10000 = s;
                } else {
                    BigInteger var16 = Secp256k1.accessFixerValue();

                    var10000 = var16.subtract(s);

                }

                BigInteger s_ = var10000;
                ByteArrayOutputStream var17 = new ByteArrayOutputStream();
                DERSequenceGenerator var18 = new DERSequenceGenerator(var17);
                var18.addObject(new ASN1Integer(r));
                var18.addObject(new ASN1Integer(s_));
                var18.close();

                return var17.toByteArray();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        public PubKey publicKey() {
            BigInteger privKey = this.priv.bitLength() > Secp256k1.accessCurve().getN().bitLength() ? this.priv.mod(Secp256k1.accessCurve().getN()) : this.priv;
            ECPoint publicPoint = (new FixedPointCombMultiplier()).multiply(Secp256k1.accessCurve().getG(), privKey);
            return new Secp256k1PublicKey(new ECPublicKeyParameters(publicPoint, Secp256k1.accessCurve()));
        }

        public int hashCode() {
            return this.priv.hashCode();
        }
    }

    public static final class Secp256k1PublicKey extends PubKey {
        private final ECPublicKeyParameters pub;

        public Secp256k1PublicKey(ECPublicKeyParameters pub) {
            super(Crypto.KeyType.Secp256k1);
            this.pub = pub;
        }


        @NonNull
        public byte[] raw() {

            return this.pub.getQ().getEncoded(true);
        }

        public boolean verify(byte[] data, byte[] signature) {

            ECDSASigner signer = new ECDSASigner();
            signer.init(false, this.pub);
            ByteArrayInputStream var27 = new ByteArrayInputStream(signature);

            ASN1Primitive var33;
            try {
                ASN1InputStream var10 = new ASN1InputStream(var27);

                ASN1Primitive var37;
                try {
                    var37 = var10.readObject();
                } finally {
                    var10.close();
                    // CloseableKt.closeFinally(var10, var36);
                }

                var33 = var37;
            } catch (Throwable var25) {
                throw new RuntimeException(var25);
            } finally {
                try {
                    var27.close();
                } catch (Throwable ignore) {
                }
            }


            if (var33 == null) {
                throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.asn1.ASN1Sequence");
            } else {
                ASN1Encodable[] var29 = ((ASN1Sequence) var33).toArray();


                if (var29.length != 2) {
                    throw new RuntimeException("Invalid signature: expected 2 values for 'r' and 's' but got " + var29.length);
                } else {
                    ASN1Primitive var10000 = var29[0].toASN1Primitive();
                    if (var10000 == null) {
                        throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.asn1.ASN1Integer");
                    } else {
                        BigInteger r = ((ASN1Integer) var10000).getValue();
                        var10000 = var29[1].toASN1Primitive();
                        if (var10000 == null) {
                            throw new NullPointerException("null cannot be cast to non-null type org.bouncycastle.asn1.ASN1Integer");
                        } else {
                            BigInteger s = ((ASN1Integer) var10000).getValue();
                            return signer.verifySignature(Hash.sha256(data), r.abs(), s.abs());
                        }
                    }
                }
            }
        }

        public int hashCode() {
            return this.pub.hashCode();
        }
    }

}
