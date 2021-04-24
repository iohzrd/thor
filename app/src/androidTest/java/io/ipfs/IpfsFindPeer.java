package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import io.LogUtils;
import io.ipfs.core.PeerInfo;
import io.ipfs.core.TimeoutCloseable;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsFindPeer {
    private static final String TAG = IpfsFindPeer.class.getSimpleName();

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


    //@Test
    public void test_local_peer() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String local = "Qmf5TsSK8dVm3btzuUrnvS8wfUW6e2vMxMRkzV9rsG6eDa";


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + local, 60);

        assertTrue(result);

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


        List<PeerId> peers = ipfs.swarmPeers();

        assertNotNull(peers);
        LogUtils.debug(TAG, "Peers : " + peers.size());
        for (PeerId peerId : peers) {

            PeerInfo peerInfo = ipfs.getPeerInfo(new TimeoutCloseable(10), peerId);

            if (peerInfo != null) {

                LogUtils.debug(TAG, peerInfo.toString());
                assertNotNull(peerInfo.getAddress());
                assertNotNull(peerInfo.getAgent());
                assertNotNull(peerInfo.getPeerId());

                Multiaddr observed = peerInfo.getObserved();
                if (observed != null) {
                        LogUtils.debug(TAG, observed.toString());
                    }
                }

                long time = System.currentTimeMillis();
            LogUtils.debug(TAG, "isConnected : " + ipfs.isConnected(peerId)
                    + " " + (System.currentTimeMillis() - time));
            }

        }


}
