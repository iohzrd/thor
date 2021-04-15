package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.LogUtils;
import io.core.ClosedException;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsRealTest {

    private static final String TAG = IpfsResolveTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_1() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);


        String key = "k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm";

        IPFS.ResolvedName res = ipfs.resolveName(() -> false, key, 0);
        assertNotNull(res);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        AtomicInteger num = new AtomicInteger(0);
        try {
            ipfs.findProviders(atomicBoolean::get, addrInfo -> {
                LogUtils.error(TAG, addrInfo.toString());
                if (num.incrementAndGet() == 5) {
                    atomicBoolean.set(true);
                }
            }, res.getHash());
            fail();
        } catch (ClosedException closedException) {
            assertTrue(atomicBoolean.get());
        }

    }
}
