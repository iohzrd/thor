package io.ipfs;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.util.Collections;

import io.ipfs.dht.ID;
import io.ipfs.dht.PeerDistanceSorter;
import io.ipfs.dht.Util;
import io.libp2p.core.PeerId;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsUtilsTest {
    private static final String TAG = IpfsCatTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void cat_utils() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerId peerId = ipfs.getPID();

        ID a = Util.ConvertPeerID(peerId);
        ID b = Util.ConvertPeerID(peerId);


        BigInteger dist = Util.Distance(a, b);
        assertEquals(dist.longValue(),0L);

        int res = Util.CommonPrefixLen(a, b);
        assertEquals(res, (a.data.length * 8));

        int cmp = a.compareTo(b);
        assertEquals(0, cmp);


        PeerId randrom = PeerId.random();
        ID r1 = Util.ConvertPeerID(randrom);
        ID r2 = Util.ConvertPeerID(randrom);

        BigInteger distCmp = Util.Distance(a, r1);
        assertTrue(distCmp.longValue() != 0L);

        int rres = Util.CommonPrefixLen(r1, r2);
        assertEquals(rres, (r1.data.length * 8));

        int rcmp = r1.compareTo(r2);
        assertEquals(0, rcmp);

        PeerDistanceSorter pds = new PeerDistanceSorter(a);
        pds.appendPeer(peerId, a);
        pds.appendPeer(randrom, r1);
        Collections.sort(pds);
        assertEquals(pds.get(0).getPeerId(), peerId);
        assertEquals(pds.get(1).getPeerId(), randrom);


        PeerDistanceSorter pds2 = new PeerDistanceSorter(a);
        pds2.appendPeer(randrom, r1);
        pds2.appendPeer(peerId, a);
        Collections.sort(pds2);
        assertEquals(pds2.get(0).getPeerId(), peerId);
        assertEquals(pds2.get(1).getPeerId(), randrom);


        PeerDistanceSorter pds3 = new PeerDistanceSorter(r1);
        pds3.appendPeer(peerId, a);
        pds3.appendPeer(randrom, r1);
        Collections.sort(pds3);
        assertEquals(pds3.get(0).getPeerId(), randrom);
        assertEquals(pds3.get(1).getPeerId(), peerId);


    }
}
