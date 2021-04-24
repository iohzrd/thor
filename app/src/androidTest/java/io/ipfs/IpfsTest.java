package io.ipfs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.LogUtils;
import io.ipfs.core.ClosedException;
import io.ipfs.core.TimeoutCloseable;
import io.ipfs.host.DnsResolver;
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
            LogUtils.debug(TAG, "Listen Address : " + ma.toString());
        }

        assertEquals(result.size(), 1); // TODO test is not correct (listen addresses should be improved)

    }



    @Test
    public void test_dns_addr() {

        if (TestEnv.isConnected(context)) {

            Set<String> result = DnsResolver.resolveDnsAddress(IPFS.LIB2P_DNS);
            assertNotNull(result);
            assertEquals(result.size(), 25);


            for (String address : result) {
                LogUtils.debug(TAG, address);
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

        String fault = ipfs.getPeerID();

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
        LogUtils.debug(TAG, hash58Base);

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
