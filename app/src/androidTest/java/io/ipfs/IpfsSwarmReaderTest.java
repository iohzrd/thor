package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.LogUtils;
import io.core.TimeoutCloseable;

import static junit.framework.TestCase.assertNotNull;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsSwarmReaderTest {


    private static final String TAG = IpfsSwarmReaderTest.class.getSimpleName();

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
    public void read() throws Exception {
        String RELAY_PID = "QmWFhiem9PnRAm9pBHQYvRqQcGAeJ2VfSFhD3JKdytiWKG";

        String cid = "QmaFuc7VmzwT5MAx3EANZiVXRtuWtTwALjgaPcSsZ2J2ip";

        IPFS ipfs = TestEnv.getTestInstance(context);

        LogUtils.error(TAG, "Connecting to RELAY ...");
        boolean success = ipfs.swarmConnect(IPFS.P2P_PATH + RELAY_PID, 10);

        LogUtils.error(TAG, "Connecting to RELAY done " + success);

        long now = System.currentTimeMillis();


        byte[] data = ipfs.loadData(cid, new TimeoutCloseable(10));
        assertNotNull(data);
        LogUtils.error(TAG, "Bytes : " + data.length / 1000 + "[kb]" +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");


    }

}
