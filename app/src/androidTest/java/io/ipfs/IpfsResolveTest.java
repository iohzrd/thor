package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.core.ClosedException;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsResolveTest {

    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_resolve_publish() throws ClosedException {
        IPFS ipfs = TestEnv.getTestInstance(context);
        String test = "Moin Wurst";
        String cid = ipfs.storeText(test);
        assertNotNull(cid);
        int random = (int) Math.abs(Math.random());


        ipfs.publishName(cid, ()-> false, random);

        String key = IPFS.IPNS_PATH + ipfs.getHost();

        IPFS.ResolvedName res = ipfs.resolveName(key, random, () -> false);
        assertNotNull(res);

        assertEquals(res.getHash(), cid);

    }

}
