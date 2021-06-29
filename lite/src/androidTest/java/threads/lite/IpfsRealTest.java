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
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.cid.Cid;
import threads.lite.core.ClosedException;
import threads.lite.core.TimeoutCloseable;
import threads.lite.format.Node;
import threads.lite.host.DnsResolver;
import threads.lite.cid.PeerId;
import threads.lite.ipns.Ipns;
import threads.lite.utils.Link;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

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
    public void test_corbett() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //CorbettReport ipns://k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm
        String key = "k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm";

        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);


        ipfs.findProviders(addrInfo -> {
                LogUtils.debug(TAG, addrInfo.toString());
                atomicBoolean.set(true);
            }, Cid.decode(res.getHash()), new TimeoutCloseable(30));


        assertTrue(atomicBoolean.get());
    }

    @Test
    public void test_blog_ipfs_io() throws ClosedException, IOException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        String link = DnsResolver.resolveDnsLink("blog.ipfs.io");

        assertNotNull(link);
        assertFalse(link.isEmpty());

        Node node = ipfs.resolveNode(link.concat("/").concat(IPFS.INDEX_HTML),
                new TimeoutCloseable(30));
        assertNotNull(node);

        String text = ipfs.getText(node.getCid(), new TimeoutCloseable(30));

        assertNotNull(text);
        assertFalse(text.isEmpty());


    }

    @Test
    public void test_unknown() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Node node = ipfs.resolveNode("QmavE42xtK1VovJFVTVkCR5Jdf761QWtxmvak9Zx718TVr",
                new TimeoutCloseable(30));
        assertNotNull(node);

        List<Link> links = ipfs.links(node.getCid(), new TimeoutCloseable(1));
        assertNotNull(links);
        assertFalse(links.isEmpty());


    }

    @Test
    public void test_unknown_2() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Node node = ipfs.resolveNode("QmfQiLpdBDbSkb2oySwFHzNucvLkHmGFxgK4oA2BUSwi4t",
                new TimeoutCloseable(30));
        assertNotNull(node);

        List<Link> links = ipfs.links(node.getCid(), new TimeoutCloseable(1));
        assertNotNull(links);
        assertFalse(links.isEmpty());
    }


    @Test
    public void test_mike() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //Mike ipns://k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4

        String key = "k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4";

        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);

        ConcurrentSkipListSet<PeerId> peers = new ConcurrentSkipListSet<>();

        ipfs.findProviders(peers::add, Cid.decode(res.getHash()), new TimeoutCloseable(30));


        assertFalse(peers.isEmpty());

        for (PeerId peerId : peers) {
            LogUtils.debug(TAG, "connect " + peerId.toBase58() + " " +
                    ipfs.swarmConnect(peerId, new TimeoutCloseable(15)));
        }

    }
}
