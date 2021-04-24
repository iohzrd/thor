package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.LogUtils;
import io.ipfs.cid.Cid;
import io.ipfs.core.ClosedException;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.format.Node;
import io.ipfs.host.DnsResolver;
import io.ipfs.utils.Link;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsRealTest {

    private static final String TAG = IpfsRealTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_1() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.reset();

        String key = "k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm";

        IPFS.ResolvedName res = ipfs.resolveName(() -> false, key, 0);
        assertNotNull(res);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        AtomicInteger num = new AtomicInteger(0);
        try {
            ipfs.findProviders(atomicBoolean::get, addrInfo -> {
                LogUtils.debug(TAG, addrInfo.toString());
                if (num.incrementAndGet() == 5) {
                    atomicBoolean.set(true);
                }
            }, Cid.Decode(res.getHash()));
            fail();
        } catch (ClosedException closedException) {
            assertTrue(atomicBoolean.get());
        }

    }

    @Test
    public void test_2() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.reset();

        String link = DnsResolver.resolveDnsLink("blog.ipfs.io");

        assertNotNull(link);
        assertFalse(link.isEmpty());

        Node node = ipfs.resolveNode(link.concat("/").concat(IPFS.INDEX_HTML), new TimeoutCloseable(30));
        String text = ipfs.getText(node.Cid(), new TimeoutCloseable(30));

        assertNotNull(text);
        assertFalse(text.isEmpty());
        LogUtils.debug(TAG, text);


    }

    @Test
    public void test_3() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.reset();

        Node node = ipfs.resolveNode("QmavE42xtK1VovJFVTVkCR5Jdf761QWtxmvak9Zx718TVr",
                new TimeoutCloseable(30));
        assertNotNull(node);

        List<Link> links = ipfs.links(node.Cid(), new TimeoutCloseable(1));
        assertNotNull(links);
        assertFalse(links.isEmpty());


    }

    @Test
    public void test_4() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.reset();

        Node node = ipfs.resolveNode("QmfQiLpdBDbSkb2oySwFHzNucvLkHmGFxgK4oA2BUSwi4t",
                new TimeoutCloseable(30));
        assertNotNull(node);

        List<Link> links = ipfs.links(node.Cid(), new TimeoutCloseable(1));
        assertNotNull(links);
        assertFalse(links.isEmpty());


    }


    @Test
    public void test_5() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);
        ipfs.reset();

        String key = "k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4";

        IPFS.ResolvedName res = ipfs.resolveName(() -> false, key, 0);
        assertNotNull(res);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        AtomicInteger num = new AtomicInteger(0);
        try {
            ipfs.findProviders(atomicBoolean::get, addrInfo -> {
                LogUtils.debug(TAG, addrInfo.toString());
                if (num.incrementAndGet() == 5) {
                    atomicBoolean.set(true);
                }
            }, Cid.Decode(res.getHash()));
            fail();
        } catch (ClosedException closedException) {
            assertTrue(atomicBoolean.get());
        }

    }
}
