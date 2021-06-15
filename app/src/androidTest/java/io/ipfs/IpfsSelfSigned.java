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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;

import io.LogUtils;
import io.ipfs.crypto.PubKey;
import io.ipfs.crypto.Rsa;
import io.ipfs.host.LiteHostCertificate;

import static junit.framework.TestCase.assertNotNull;
import static net.luminis.tls.TlsConstants.NamedGroup.secp256r1;


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

        final KeyPair keypair;

        KeyPairGenerator keyPairGenerator;
        keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec(secp256r1.toString()));

        keypair = keyPairGenerator.genKeyPair();


        Rsa.RsaPrivateKey privateKey = Rsa.generateRsaKeyPair(2048, new SecureRandom()).first;
        X509Certificate cert = new LiteHostCertificate(context, privateKey, keypair).cert();

        assertNotNull(cert);

        PubKey pubKey = LiteHostCertificate.extractPublicKey(cert);
        assertNotNull(pubKey);

    }

}
