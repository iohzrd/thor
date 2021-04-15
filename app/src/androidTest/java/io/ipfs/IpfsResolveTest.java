package io.ipfs;


import android.annotation.SuppressLint;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.primitives.Bytes;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.LogUtils;
import io.core.ClosedException;
import io.core.TimeoutCloseable;
import io.ipfs.multihash.Multihash;
import io.ipns.IpnsValidator;
import io.libp2p.core.PeerId;

import static junit.framework.TestCase.assertNotNull;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsResolveTest {
    private static final String TAG = IpfsResolveTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_resolve_publish() throws ClosedException {
        IPFS ipfs = TestEnv.getTestInstance(context);

        String test = "Moin Wurst";
        String cid = ipfs.storeText(test);
        assertNotNull(cid);
        int random = (int) Math.abs(Math.random());

        long start = System.currentTimeMillis();
        try {
            ipfs.publishName(new TimeoutCloseable(30), cid, random);
        } catch (ClosedException ignore){
            // ignore
        }
        LogUtils.error(TAG, "Time publish name " +  (System.currentTimeMillis() - start));

        String key = ipfs.getHost();

        IPFS.ResolvedName res = ipfs.resolveName(() -> false, key, random);
        assertNotNull(res);

        assertEquals(res.getHash(), cid);

    }

    @Test
    public void test_time_format() throws ParseException {
        @SuppressLint("SimpleDateFormat") String format = new SimpleDateFormat(
                IPFS.TimeFormatIpfs).format(new Date(System.currentTimeMillis()));
        assertNotNull(format);

        Date date = IpnsValidator.getDate(format);
        Objects.requireNonNull(date);


        Date cmp = IpnsValidator.getDate("2021-04-15T06:14:21.184394868Z");
        Objects.requireNonNull(cmp);

    }
    @Test
    public void test_peer_id() throws IOException {
        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerId peerId = ipfs.getPID();
        assertNotNull(peerId);

        byte[] data = peerId.getBytes();


        Multihash mh = Multihash.deserialize(data);

        PeerId cmp = PeerId.fromBase58(mh.toBase58());

        assertEquals(cmp, peerId);

        byte[] ipns = IPFS.IPNS_PATH.getBytes();
        String cipns = new String(ipns);

        assertEquals(cipns, IPFS.IPNS_PATH);

        byte[] result = Bytes.concat(ipns, data);

        int index = Bytes.indexOf(result, ipns);
        assertEquals(index, 0);

        byte[] key = Arrays.copyOfRange(result, ipns.length, result.length);

        Multihash mh1 = Multihash.deserialize(key);

        PeerId cmp1 = PeerId.fromBase58(mh1.toBase58());

        assertEquals(cmp1, peerId);
    }

}
