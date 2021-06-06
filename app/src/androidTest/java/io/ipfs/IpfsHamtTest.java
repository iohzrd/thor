package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.core.ClosedException;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.format.Node;
import io.ipfs.host.DnsResolver;
import io.ipfs.host.PeerId;
import io.ipfs.ipns.Ipns;
import io.ipfs.utils.Link;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsHamtTest {

    private static final String TAG = IpfsHamtTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    //@Test
    public void test_wasser() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        String cid = "bafkreihzzvgek3jhvcdnleazdixy7v2upbyjiyds44bljkwlfqtfi2oqii";


        boolean result = ipfs.isDir(Cid.decode(cid), new TimeoutCloseable(60));

        assertFalse(result);
    }


    @Test
    public void test_wikipedia() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        String link = DnsResolver.resolveDnsLink("en.wikipedia-on-ipfs.org");

        assertNotNull(link);
        assertFalse(link.isEmpty());

        Node node = ipfs.resolveNode(link, new TimeoutCloseable(60));
        assertNotNull(node);


        boolean result = ipfs.isDir(node.getCid(), new TimeoutCloseable(60));
        assertTrue(result);


        List<Link> links = ipfs.getLinks(node.getCid(), new TimeoutCloseable(60));
        assertNotNull(links);
        assertFalse(links.isEmpty());
        for (Link ln:links) {
            LogUtils.error(TAG, ln.toString());
        }

    }



}
