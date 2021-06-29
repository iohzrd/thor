package threads.lite;


import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.luminis.quic.stream.QuicStream;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.cid.Cid;
import threads.lite.core.TimeoutCloseable;
import threads.lite.cid.PeerId;


import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsRelayTest {
    private static final String TAG = IpfsRelayTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_relay_canHop() {
        IPFS ipfs = TestEnv.getTestInstance(context);
        long start = System.currentTimeMillis();


        PeerId relay = PeerId.fromBase58("QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei");
        boolean result = ipfs.swarmConnect(IPFS.P2P_PATH + relay.toBase58(),
                IPFS.CONNECT_TIMEOUT);
        assertTrue(result);

        try {
            result = ipfs.canHop(relay, new TimeoutCloseable(10));
            assertTrue(result);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            fail();
        } finally {
            LogUtils.info(TAG, "Time " + (System.currentTimeMillis() - start));
        }

        start = System.currentTimeMillis();
        relay = PeerId.fromBase58("Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh");

        result = ipfs.swarmConnect(IPFS.P2P_PATH + relay.toBase58(),
                IPFS.CONNECT_TIMEOUT);
        assertTrue(result);


        try {
            result = ipfs.canHop(relay, new TimeoutCloseable(10));
            assertTrue(result);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            fail();
        } finally {
            LogUtils.info(TAG, "Time " + (System.currentTimeMillis() - start));
        }
    }

    // @Test (not working, waiting for new relay + punchhole concept)
    public void test_findRelays() {
        IPFS ipfs = TestEnv.getTestInstance(context);
        long start = System.currentTimeMillis();

        try {
            AtomicBoolean find = new AtomicBoolean(false);

            ipfs.findProviders(peerId -> find.set(true), Cid.nsToCid(IPFS.RELAY_RENDEZVOUS),
                    new TimeoutCloseable(120));

            LogUtils.info(TAG, "NumSwarmPeers " + ipfs.numConnections());

            assertTrue(find.get());
        } catch (Throwable throwable) {
            fail();
        } finally {
            LogUtils.info(TAG, "Time " + (System.currentTimeMillis() - start));
        }
    }

    @Test
    public void test_relay_dialPeer() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerId peerId = PeerId.fromBase58("12D3KooWLfmzMdAje4F6F6q68jYRatu1JQaz2KB4j8ambYahd1xh");


        AtomicBoolean succes = new AtomicBoolean(false);

        try {
            QuicStream stream = ipfs.getHost().getRelayStream(new TimeoutCloseable(15),
                    peerId);
            if (stream != null) {
                succes.set(true);


               // TODO stream.writeAndFlush(DataHandler.writeToken(IPFS.STREAM_PROTOCOL));
               // TODO stream.writeAndFlush(DataHandler.writeToken(IPFS.IDENTITY_PROTOCOL));

                Thread.sleep(500);
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        assertTrue(succes.get());

    }
}
