package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.core.ClosedException;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.core.TimeoutProgress;
import io.ipfs.host.PeerId;
import io.ipfs.multiaddr.Multiaddr;
import io.ipfs.utils.Progress;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertFalse;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsStressTest {
    private static final String TAG = IpfsStressTest.class.getSimpleName();



    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }



    @Test
    public void stress_test0() throws ClosedException, IOException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        AtomicLong time = new AtomicLong(System.currentTimeMillis());
        byte[] data = ipfs.getData(Cid.Decode("QmcniBv7UQ4gGPQQW2BwbD4ZZHzN3o3tPuNLZCbBchd1zh"),
                new Progress(){
                    @Override
                    public boolean isClosed() {
                        return time.get() < (System.currentTimeMillis() - 20000);
                    }

                    @Override
                    public void setProgress(int progress) {
                        LogUtils.error(TAG, "Progress " + progress);
                        time.set(System.currentTimeMillis());
                    }

                    @Override
                    public boolean doProgress() {
                        return true;
                    }
                });
        assertNotNull(data);


    }

}
