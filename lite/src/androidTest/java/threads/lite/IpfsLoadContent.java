package threads.lite;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import junit.framework.TestCase;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import threads.lite.core.ClosedException;
import threads.lite.core.TimeoutCloseable;
import threads.lite.format.Node;
import threads.lite.ipns.Ipns;

import static junit.framework.TestCase.assertNotNull;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsLoadContent {
    private static final String TAG = IpfsLoadContent.class.getSimpleName();


    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void find_peer_mike() throws ClosedException, IOException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //Mike ipns://k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4

        String key = "k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4";

        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());

        String link = IPFS.IPFS_PATH.concat(res.getHash().concat("/").concat(IPFS.INDEX_HTML));
        LogUtils.debug(TAG, link);
        Node node = ipfs.resolveNode(link, new TimeoutCloseable(60));
        assertNotNull(node);

        String text = ipfs.getText(node.getCid(), new TimeoutCloseable(30));

        assertNotNull(text);
        TestCase.assertFalse(text.isEmpty());

    }

    @Test
    public void find_peer_corbett() throws ClosedException, IOException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //CorbettReport ipns://k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm

        String key = "k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm";

        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());

        String link = IPFS.IPFS_PATH.concat(res.getHash().concat("/").concat(IPFS.INDEX_HTML));
        LogUtils.debug(TAG, link);
        Node node = ipfs.resolveNode(link, new TimeoutCloseable(60));
        assertNotNull(node);

        String text = ipfs.getText(node.getCid(), new TimeoutCloseable(30));

        assertNotNull(text);
        TestCase.assertFalse(text.isEmpty());
    }

    @Test
    public void find_peer_freedom() throws ClosedException, IOException {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //FreedomsPhoenix.com ipns://k2k4r8magsykrprepvtuvd1h8wonxy7rbdkxd09aalsvclqh7wpb28m1

        String key = "k2k4r8magsykrprepvtuvd1h8wonxy7rbdkxd09aalsvclqh7wpb28m1";

        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());

        String link = IPFS.IPFS_PATH.concat(res.getHash().concat("/").concat(IPFS.INDEX_HTML));
        LogUtils.debug(TAG, link);
        Node node = ipfs.resolveNode(link, new TimeoutCloseable(60));
        assertNotNull(node);

        String text = ipfs.getText(node.getCid(), new TimeoutCloseable(30));

        assertNotNull(text);
        TestCase.assertFalse(text.isEmpty());
    }

    @Test
    public void find_peer_pirates() throws IOException, ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);


        //PiratesWithoutBorders.com ipns://k2k4r8l8zgv45qm2sjt7p16l7pvy69l4jr1o50cld4s98wbnanl0zn6t

        String key = "k2k4r8l8zgv45qm2sjt7p16l7pvy69l4jr1o50cld4s98wbnanl0zn6t";

        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());

        String link = IPFS.IPFS_PATH.concat(res.getHash().concat("/").concat(IPFS.INDEX_HTML));
        LogUtils.debug(TAG, link);
        Node node = ipfs.resolveNode(link, new TimeoutCloseable(60));
        assertNotNull(node);

        String text = ipfs.getText(node.getCid(), new TimeoutCloseable(30));

        assertNotNull(text);
        TestCase.assertFalse(text.isEmpty());
    }
}
