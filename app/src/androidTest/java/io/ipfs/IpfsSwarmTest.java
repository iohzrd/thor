package io.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.LogUtils;

import static junit.framework.TestCase.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class IpfsSwarmTest {

    private static final String TAG = IpfsSwarmTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void dummy() {
        assertNotNull(context);
    }


    @Test
    public void test_find_peers() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);


        java.lang.Thread.sleep(10000);

        AtomicInteger atomicInteger = new AtomicInteger(0);

        List<String> foundPeers = new ArrayList<>();
        while (foundPeers.size() < 3 && atomicInteger.incrementAndGet() < 10) {


            List<String> peers = ipfs.swarmPeers();
            for (String peer : peers) {


                LogUtils.error(TAG, "Peer " + peer);


                if (!foundPeers.contains(peer)) {
                    foundPeers.add(peer);
                }


                LogUtils.error(TAG, "Connect to peer : " + ipfs.swarmConnect(
                        IPFS.P2P_PATH + peer, 2));


            }


            Thread.sleep(5000);


        }


        for (String peer : foundPeers) {
            LogUtils.error(TAG, "Found peer : " + peer);
        }
    }
}
