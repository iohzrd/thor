package io.ipfs;


import android.annotation.SuppressLint;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Date;

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



        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(new Date(System.currentTimeMillis()));
        assertNotNull(format);
        String test = "Moin Wurst";
        String cid = ipfs.storeText(test);
        assertNotNull(cid);
        int random = (int) Math.abs(Math.random());


        ipfs.publishName(cid, ()-> false, random);

        String key = ipfs.getHost();

        IPFS.ResolvedName res = ipfs.resolveName(key, random, () -> false);
        assertNotNull(res);

        assertEquals(res.getHash(), cid);

    }

}
