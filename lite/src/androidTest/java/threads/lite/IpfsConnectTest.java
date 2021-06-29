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
import threads.lite.host.PeerInfo;
import threads.lite.cid.Multiaddr;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertFalse;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsConnectTest {
    private static final String TAG = IpfsConnectTest.class.getSimpleName();

    private static final String DUMMY_PID = "QmVLnkyetpt7JNpjLmZX21q9F8ZMnhBts3Q53RcAGxWH6V";

    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void swarm_connect() {

        IPFS ipfs = TestEnv.getTestInstance(context);
        String pc = "QmRxoQNy1gNGMM1746Tw8UBNBF8axuyGkzcqb2LYFzwuXd";

        // TIMEOUT not working
        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + pc, 6);
        assertFalse(result);

    }

    @Test
    public void test_swarm_connect() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerId relay = ipfs.getPeerId("QmchgNzyUFyf2wpfDMmpGxMKHA3PkC1f3H2wUgbs21vXoz");


        boolean connected = ipfs.isConnected(relay);
        assertFalse(connected);

        LogUtils.debug(TAG, "Stage 1");

        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + relay, 1);
        assertFalse(result);

        LogUtils.debug(TAG, "Stage 2");

        result = ipfs.swarmConnect(IPFS.P2P_PATH + relay, 10);
        assertFalse(result);

        LogUtils.debug(TAG, "Stage 3");

        relay = ipfs.getPeerId(DUMMY_PID);
        result = ipfs.swarmConnect(IPFS.P2P_PATH + relay, 10);
        assertFalse(result);

        LogUtils.debug(TAG, "Stage 4");

    }


    @Test
    public void test_print_swarm_peers() {
        IPFS ipfs = TestEnv.getTestInstance(context);


        Set<PeerId> peers = ipfs.connectedPeers();

        assertNotNull(peers);
        LogUtils.debug(TAG, "Peers : " + peers.size());
        for (PeerId peerId : peers) {

            try {
                PeerInfo peerInfo = ipfs.getPeerInfo(peerId, new TimeoutCloseable(10));


                LogUtils.debug(TAG, peerInfo.toString());
                assertNotNull(peerInfo.getAddresses());
                assertNotNull(peerInfo.getAgent());
                assertNotNull(peerInfo.getPeerId());

                Multiaddr observed = peerInfo.getObserved();
                if (observed != null) {
                    LogUtils.debug(TAG, observed.toString());
                }

            } catch (Throwable throwable) {
                LogUtils.debug(TAG, "" + throwable.getClass().getName());
            }

            long time = System.currentTimeMillis();
            LogUtils.debug(TAG, "isConnected : " + ipfs.isConnected(peerId)
                    + " " + (System.currentTimeMillis() - time));
        }

    }


}
