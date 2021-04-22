package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.LogUtils;
import io.core.TimeoutCloseable;
import io.libp2p.PeerInfo;
import io.libp2p.core.PeerId;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsRelayTest {
    private static final String TAG = IpfsRelayTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_relay_canHop() {
        IPFS ipfs = TestEnv.getTestInstance(context);
        long start = System.currentTimeMillis();
        PeerId relay = PeerId.fromBase58("Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh");
        try {

            PeerInfo peerInfo = ipfs.getPeerInfo(new TimeoutCloseable(10), relay);


            boolean result = ipfs.canHop(new TimeoutCloseable(10), relay);
            assertTrue(result);

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            fail();
        } finally {
            LogUtils.info(TAG, "Time " + (System.currentTimeMillis() - start));
        }
    }
}
