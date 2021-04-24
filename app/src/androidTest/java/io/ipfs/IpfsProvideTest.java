package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import io.ipfs.core.ClosedException;
import io.ipfs.core.TimeoutCloseable;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsProvideTest {
    private static final String TAG = IpfsProvideTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_resolve_provide() throws ClosedException {
        IPFS ipfs = TestEnv.getTestInstance(context);

        LogUtils.error(TAG, ipfs.getPeerID());
        String test = "Moin Wurdfasdfsadfasst jdöldöflas" + Math.random();
        String cid = ipfs.storeText(test);
        assertNotNull(cid);

        long start = System.currentTimeMillis();
        try {
            ipfs.provide(new TimeoutCloseable(30), cid);
        } catch (ClosedException ignore) {
        }
        LogUtils.debug(TAG, "Time provide " + (System.currentTimeMillis() - start));

        long time = System.currentTimeMillis();
        AtomicBoolean finished = new AtomicBoolean(false);
        try {
            ipfs.findProviders(finished::get, peerId -> finished.set(true), cid);
        } catch (ClosedException ignore) {
        }
        LogUtils.debug(TAG, "Time Providers : " + (System.currentTimeMillis() - time) + " [ms]");
        assertTrue(finished.get());
    }
}
