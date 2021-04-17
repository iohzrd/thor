package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import io.LogUtils;
import io.core.ClosedException;
import io.core.TimeoutCloseable;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsProvideTest {
    private static final String TAG = IpfsResolveTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_resolve_provide() throws ClosedException {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String test = "Moin Wurdfasdfsadfasst jdöldöflas" + Math.random();
        String cid = ipfs.storeText(test);
        assertNotNull(cid);

        long start = System.currentTimeMillis();
        try {
            ipfs.provide(new TimeoutCloseable(120), cid);
            fail();
        } catch (ClosedException ignore){
            // ignore
        }
        LogUtils.error(TAG, "Time provide " +  (System.currentTimeMillis() - start));

        long time = System.currentTimeMillis();
        List<String> provs = new ArrayList<>();
        ipfs.findProviders(()-> false, addrInfo -> provs.add(addrInfo.getPeerId().toBase58()), cid);
        for (String prov : provs) {
            LogUtils.error(TAG, "Provider " + prov);
        }
        LogUtils.error(TAG, "Time Providers : " + (System.currentTimeMillis() - time) + " [ms]");
        assertFalse(provs.isEmpty());
    }
}