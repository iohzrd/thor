package io.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Future;

import io.ipfs.host.PeerId;
import io.ipfs.multiformats.Multiaddr;
import io.netty.incubator.codec.quic.QuicChannel;

import static junit.framework.TestCase.assertNotNull;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsQuicTest {


    private static final String TAG = IpfsQuicTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_1() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Multiaddr multiaddr = new Multiaddr("/ip4/147.75.109.213/udp/4001/quic/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN");

        Future<QuicChannel> conn = ipfs.getHost().dial(multiaddr, PeerId.fromBase58("QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN"));
        assertNotNull(conn);
        QuicChannel channel = conn.get();
        assertNotNull(channel);

    }

}


