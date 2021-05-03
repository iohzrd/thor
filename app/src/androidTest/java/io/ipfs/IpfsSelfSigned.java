package io.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.primitives.Bytes;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.util.Integers;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;


import crypto.pb.Crypto;
import io.LogUtils;
import io.ipfs.host.LiteSignedCertificate;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.EcdsaKt;
import io.libp2p.crypto.keys.Ed25519Kt;
import io.libp2p.crypto.keys.RsaKt;
import io.libp2p.crypto.keys.RsaPrivateKey;
import io.libp2p.crypto.keys.Secp256k1Kt;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsSelfSigned {
    private static final String TAG = IpfsTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_certificate() throws Exception {


        LogUtils.error(TAG, LiteSignedCertificate.integersToString(LiteSignedCertificate.extensionID));

        ASN1ObjectIdentifier ident = new ASN1ObjectIdentifier(
                LiteSignedCertificate.integersToString(LiteSignedCertificate.extensionID));

        assertNotNull(ident);

       // IPFS ipfs = TestEnv.getTestInstance(context);

        //X509Certificate cert = ipfs.getSelfSignedCertificate().cert();


        String algorithm = "RSA";
        final KeyPair keypair;

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
        keyGen.initialize(2048, LiteSignedCertificate.ThreadLocalInsecureRandom.current());
        keypair = keyGen.generateKeyPair();

        RsaPrivateKey privateKey = new RsaPrivateKey(keypair.getPrivate(), keypair.getPublic());
        X509Certificate cert = new LiteSignedCertificate(privateKey, keypair).cert();

        assertNotNull(cert);

        PubKey pubKey = LiteSignedCertificate.extractPublicKey(cert);
        assertNotNull(pubKey);

    }

}
