package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import io.LogUtils;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.host.PeerId;
import io.ipfs.host.PeerInfo;
import io.ipfs.multiaddr.Multiaddr;

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
                IPFS.CONNECT_TIMEOUT * 10);
        LogUtils.debug(TAG, relay.toBase58() + " " + result);

        Set<Multiaddr> addresses = ipfs.getAddresses(relay);
        assertFalse(addresses.isEmpty());

        for (Multiaddr addr:addresses) {
            LogUtils.debug(TAG, addr.toString());
        }


    }

    //@Test
    public void find_peer_text1() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        String key = "k2k4r8jllj4k33jxoa4vaeleqkrwu8b7tqz7tgczhptbfkhqr2i280fm";

        IPFS.ResolvedName res = ipfs.resolveName(key, 0, new TimeoutCloseable(30));
        assertNotNull(res);

        LogUtils.debug(TAG, res.toString());


        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + res.getPeerId().toBase58(),
                IPFS.CONNECT_TIMEOUT);
        LogUtils.debug(TAG, res.getPeerId().toBase58() + " " + result);

    }

}
