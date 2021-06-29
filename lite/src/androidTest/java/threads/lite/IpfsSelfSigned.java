package threads.lite;

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

import threads.lite.crypto.PubKey;
import threads.lite.crypto.Rsa;
import threads.lite.host.LiteHostCertificate;

import static junit.framework.TestCase.assertNotNull;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsSelfSigned {
    private static final String TAG = IpfsSelfSigned.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_certificate() throws Exception {


        LogUtils.debug(TAG, LiteHostCertificate.integersToString(LiteHostCertificate.extensionID));

        ASN1ObjectIdentifier ident = new ASN1ObjectIdentifier(
                LiteHostCertificate.integersToString(LiteHostCertificate.extensionID));

        assertNotNull(ident);

        IPFS ipfs = TestEnv.getTestInstance(context);
        assertNotNull(ipfs);

        String algorithm = "RSA";
        final KeyPair keypair;

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
        keyGen.initialize(2048, LiteHostCertificate.ThreadLocalInsecureRandom.current());
        keypair = keyGen.generateKeyPair();

        Rsa.RsaPrivateKey privateKey = new Rsa.RsaPrivateKey(keypair.getPrivate(), keypair.getPublic());
        X509Certificate cert = new LiteHostCertificate(context, privateKey, keypair).cert();

        assertNotNull(cert);

        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
        assertNotNull(pubKey);

    }

}
