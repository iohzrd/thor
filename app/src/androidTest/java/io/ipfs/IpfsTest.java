package io.ipfs;

import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;

import io.LogUtils;
import io.core.ClosedException;
import io.core.TimeoutCloseable;
import io.ipfs.utils.Link;
import io.libp2p.core.multiformats.Multiaddr;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsTest {
    private static final String TAG = IpfsTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_listenAddresses() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        List<Multiaddr> result = ipfs.listenAddresses();
        assertNotNull(result);
        for (Multiaddr ma:result) {
            LogUtils.error(TAG, "Listen Address : " + ma.toString());
        }

        assertEquals(result.size(), 1); // TODO test is not correct (listen addresses should be improved)

    }

    @Test
    public void test_dnsAddress() throws ClosedException {
        IPFS ipfs = TestEnv.getTestInstance(context);



        boolean result = ipfs.swarmConnect(
                "/dnsaddr/bootstrap.libp2p.io/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN",
                ()-> false);
        assertTrue(result);



    }

    @Test
    public void test_versionAndPID() {
        IPFS ipfs = TestEnv.getTestInstance(context);


        String pid = IPFS.getPeerID(context);
        LogUtils.error(TAG, Objects.requireNonNull(pid));

        assertEquals(ipfs.getPeerID(), pid);
    }

    @Test
    public void test_dns_addr() {

        if (TestEnv.isConnected(context)) {

            Pair<List<String>, List<String>> result = DnsAddrResolver.getMultiAddresses();
            List<String> first = result.first;
            List<String> second = result.second;
            assertNotNull(first);
            assertEquals(first.size(), 0);
            assertNotNull(second);
            assertEquals(second.size(), 5);


            for (String address : first) {
                LogUtils.error(TAG, address);
            }


            for (String address : second) {
                LogUtils.error(TAG, address);
            }
        }
    }

    @Test
    public void streamTest() throws Exception {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String test = "Moin";
        String cid = ipfs.storeText(test);
        assertNotNull(cid);
        byte[] bytes = ipfs.getData(cid, () -> false);
        assertNotNull(bytes);
        assertEquals(test, new String(bytes));

        String fault = Objects.requireNonNull(IPFS.getPeerID(context));

        try {
            ipfs.loadData(fault, new TimeoutCloseable(10));
            fail();
        } catch (Exception ignore) {
            // ok
        }


    }

    @Test
    public void test_timeout_cat() throws Exception {

        String notValid = "QmaFuc7VmzwT5MAx3EANZiVXRtuWtTwALjgaPcSsZ2Jdip";
        IPFS ipfs = TestEnv.getTestInstance(context);

        try {
            ipfs.loadData(notValid, new TimeoutCloseable(10));
        } catch (Exception ignore) {
            // ok
        }

    }


    private byte[] getRandomBytes() {
        return RandomStringUtils.randomAlphabetic(400000).getBytes();
    }

    @Test
    public void test_add_cat() {
        IPFS ipfs = TestEnv.getTestInstance(context);

        byte[] content = getRandomBytes();

        String hash58Base = ipfs.storeData(content);
        assertNotNull(hash58Base);
        LogUtils.error(TAG, hash58Base);

        byte[] fileContents = ipfs.getData(hash58Base, () -> false);
        assertNotNull(fileContents);
        assertEquals(content.length, fileContents.length);
        assertEquals(new String(content), new String(fileContents));

        ipfs.rm(hash58Base, true);

    }


    @Test
    public void test_ls_timeout() {

        IPFS ipfs = TestEnv.getTestInstance(context);

        try {
            ipfs.getLinks(
                    "QmXm3f7uKuFKK3QUL1V1oJZnpJSYX8c3vdhd94evSQUPCH",
                    new TimeoutCloseable(20));
            fail();
        } catch (ClosedException closedException) {
            return;
        }
        fail();

    }

    @Test
    public void test_ls_small() throws ClosedException {

        IPFS ipfs = TestEnv.getTestInstance(context);


        String cid = ipfs.storeText("hallo");
        assertNotNull(cid);
        List<Link> links = ipfs.getLinks(cid, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 0);
        links = ipfs.getLinks(cid, new TimeoutCloseable(20));
        assertNotNull(links);
        assertEquals(links.size(), 0);
    }
}
