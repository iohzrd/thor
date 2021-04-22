package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.ipfs.utils.Reachable;

import static org.junit.Assert.assertSame;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsIdentifyService {
    private static final String TAG = IpfsIdentifyService.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void identify_test() {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.setReachable(Reachable.UNKNOWN);
        Reachable res = ipfs.getReachable();
        assertSame(res, Reachable.UNKNOWN);


        res = ipfs.evaluateReachable();
        assertSame(res, Reachable.PRIVATE); // at least on my smartphone

    }
}
