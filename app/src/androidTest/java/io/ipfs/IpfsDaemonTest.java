package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

import io.LogUtils;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class IpfsDaemonTest {
    private static final String TAG = IpfsDaemonTest.class.getSimpleName();
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
    public void testConnectionBytes() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String content = getRandomString();
        String hash58Base = ipfs.storeText(content);
        LogUtils.error(TAG, Objects.requireNonNull(hash58Base));

        byte[] contentLocal = ipfs.getData(hash58Base, () -> false);
        assertEquals(content, new String(Objects.requireNonNull(contentLocal)));


    }

    private String getRandomString() {
        return "" + RandomStringUtils.randomAscii(100);
    }

}
