package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import io.LogUtils;
import io.libp2p.core.multiformats.Multiaddr;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsAutoRelayTest {

    private static final String TAG = IpfsAutoRelayTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void dummy() {
        assertNotNull(context);
    }

    //@Test
    public void testAutoRelay() {


        IPFS ipfs = TestEnv.getTestInstance(context);


        AtomicInteger counter = new AtomicInteger(0);
        while (counter.incrementAndGet() < 40) {


            boolean result = ipfs.swarmConnect(
                    "/ip4/2.207.31.127/tcp/4001/ipfs/QmPdjjouL3gJHEAJxosF964QQYG6AXCsVoeXktaKkN6i51", 10);
            assertTrue(result);


            Multiaddr peer = ipfs.swarmPeer("QmPdjjouL3gJHEAJxosF964QQYG6AXCsVoeXktaKkN6i51");


            //assertNotNull(peer);

            if (peer != null) {
                LogUtils.error(TAG, "" + peer.toString());
            }


            ipfs.swarmConnect("/ip4/18.224.32.43/tcp/4001/ipfs/QmaXbbcs7LRFuEoQcxfXqziZATzS68WT5DgFjYFgn3YYLX", 10);


            ipfs.swarmPeers();


        }


    }
}
