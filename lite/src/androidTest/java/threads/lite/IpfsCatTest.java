package threads.lite;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import threads.lite.cid.Cid;
import threads.lite.core.ClosedException;
import threads.lite.core.TimeoutCloseable;
import threads.lite.core.TimeoutProgress;
import threads.lite.cid.PeerId;
import threads.lite.utils.Link;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsCatTest {

    private static final String TAG = IpfsCatTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void cat_test() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);
        Cid cid = Cid.decode("Qmaisz6NMhDB51cCvNWa1GMS7LU1pAxdF4Ld6Ft9kZEP2a");
        long time = System.currentTimeMillis();
        ConcurrentSkipListSet<PeerId> provs = new ConcurrentSkipListSet<>();

        ipfs.findProviders(provs::add, cid, new TimeoutCloseable(45));

        assertFalse(provs.isEmpty());

        for (PeerId prov : provs) {
            LogUtils.debug(TAG, "Provider " + prov.toBase58());
        }
        LogUtils.debug(TAG, "Time Providers : " + (System.currentTimeMillis() - time) + " [ms]");


        time = System.currentTimeMillis();


        List<Link> res = ipfs.getLinks(cid, new TimeoutCloseable(30));
        LogUtils.debug(TAG, "Time : " + (System.currentTimeMillis() - time) + " [ms]");
        assertNotNull(res);
        assertTrue(res.isEmpty());

        time = System.currentTimeMillis();
        byte[] content = ipfs.getData(cid, new TimeoutProgress(10) {
            @Override
            public void setProgress(int progress) {
                LogUtils.debug(TAG, "" + progress);
            }

            @Override
            public boolean doProgress() {
                return true;
            }
        });

        LogUtils.debug(TAG, "Time : " + (System.currentTimeMillis() - time) + " [ms]");

        assertNotNull(content);


        time = System.currentTimeMillis();
        ipfs.rm(cid);
        LogUtils.debug(TAG, "Time : " + (System.currentTimeMillis() - time) + " [ms]");

    }


    @Test
    public void cat_not_exist() {


        IPFS ipfs = TestEnv.getTestInstance(context);
        Cid cid = Cid.decode("QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nt");
        try {
            ipfs.getData(cid, new TimeoutCloseable(10));
            fail();
        } catch (Exception ignore) {
            //
        }
    }


    @Test
    public void cat_test_local() throws IOException, ClosedException {


        IPFS ipfs = TestEnv.getTestInstance(context);

        Cid local = ipfs.storeText("Moin Moin Moin");
        assertNotNull(local);


        byte[] content = ipfs.getData(local, () -> false);

        assertNotNull(content);

    }

    @Test
    public void cat_empty() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Cid data = ipfs.storeText("");
        assertNotNull(data);


        Cid cid = Cid.decode("QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nn");
        List<Link> res = ipfs.getLinks(cid, new TimeoutCloseable(30));
        assertNotNull(res);

        assertTrue(res.isEmpty());
        try {
            ipfs.getData(cid, new TimeoutCloseable(10));
            fail();
        } catch (Exception ignore) {
            //
        }

        ipfs.rm(cid);

    }
}