package threads.lite;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import threads.lite.core.TimeoutCloseable;
import threads.lite.cid.PeerId;
import threads.lite.ipns.Ipns;
import threads.lite.cid.Multiaddr;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsFindPeer {
    private static final String TAG = IpfsFindPeer.class.getSimpleName();


    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void find_peer_test0() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerId relay = PeerId.fromBase58("QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei");

        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + relay.toBase58(),
                30);
        LogUtils.debug(TAG, relay.toBase58() + " " + result);
        assertTrue(result);

        Set<Multiaddr> addresses = ipfs.getAddresses(relay);
        assertFalse(addresses.isEmpty());

        for (Multiaddr addr : addresses) {
            LogUtils.debug(TAG, addr.toString());
        }


    }


    @Test
    public void find_peer_mike() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //Mike ipns://k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4

        String key = "k2k4r8n098cwalcc7rdntd19nsjyzh6rku1hvgsmkjzvnw582mncc4b4";
        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + res.getPeerId().toBase58(),
                30);
        LogUtils.debug(TAG, res.getPeerId().toBase58() + " " + result);


        Set<Multiaddr> addresses = ipfs.getAddresses(res.getPeerId());
        assertFalse(addresses.isEmpty());

        for (Multiaddr addr : addresses) {
            LogUtils.debug(TAG, addr.toString());
        }
    }

    @Test
    public void find_peer_corbett() {

        IPFS ipfs = TestEnv.getTestInstance(context);


        //CorbettReport ipns://k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm

        String key = "k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm";
        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + res.getPeerId().toBase58(),
                30);
        LogUtils.debug(TAG, res.getPeerId().toBase58() + " " + result);


        Set<Multiaddr> addresses = ipfs.getAddresses(res.getPeerId());
        assertFalse(addresses.isEmpty());

        for (Multiaddr addr : addresses) {
            LogUtils.debug(TAG, addr.toString());
        }
    }

    @Test
    public void find_peer_freedom() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        //FreedomsPhoenix.com ipns://k2k4r8magsykrprepvtuvd1h8wonxy7rbdkxd09aalsvclqh7wpb28m1

        String key = "k2k4r8magsykrprepvtuvd1h8wonxy7rbdkxd09aalsvclqh7wpb28m1";
        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + res.getPeerId().toBase58(),
                30);
        LogUtils.debug(TAG, res.getPeerId().toBase58() + " " + result);


        Set<Multiaddr> addresses = ipfs.getAddresses(res.getPeerId());
        assertFalse(addresses.isEmpty());

        for (Multiaddr addr : addresses) {
            LogUtils.debug(TAG, addr.toString());
        }
    }

    @Test
    public void find_peer_pirates() {

        IPFS ipfs = TestEnv.getTestInstance(context);


        //PiratesWithoutBorders.com ipns://k2k4r8l8zgv45qm2sjt7p16l7pvy69l4jr1o50cld4s98wbnanl0zn6t

        String key = "k2k4r8l8zgv45qm2sjt7p16l7pvy69l4jr1o50cld4s98wbnanl0zn6t";
        Ipns.Entry res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);
        LogUtils.debug(TAG, res.toString());


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + res.getPeerId().toBase58(),
                IPFS.CONNECT_TIMEOUT);
        LogUtils.debug(TAG, res.getPeerId().toBase58() + " " + result);


        Set<Multiaddr> addresses = ipfs.getAddresses(res.getPeerId());
        assertFalse(addresses.isEmpty());

        for (Multiaddr addr : addresses) {
            LogUtils.debug(TAG, addr.toString());
        }
    }
}
