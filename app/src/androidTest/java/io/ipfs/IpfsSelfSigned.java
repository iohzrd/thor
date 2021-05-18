package io.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;

import io.LogUtils;
import io.crypto.PubKey;
import io.crypto.Rsa;
import io.ipfs.host.LiteSignedCertificate;

import static junit.framework.TestCase.assertNotNull;


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

        Rsa.RsaPrivateKey privateKey = new Rsa.RsaPrivateKey(keypair.getPrivate(), keypair.getPublic());
        X509Certificate cert = new LiteSignedCertificate(privateKey, keypair).cert();

        assertNotNull(cert);

        PubKey pubKey = LiteSignedCertificate.extractPublicKey(cert);
        assertNotNull(pubKey);

    }

}
