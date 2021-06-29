package threads.lite;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import threads.lite.cid.Cid;
import threads.lite.core.ClosedException;
import threads.lite.core.TimeoutCloseable;
import threads.lite.utils.Link;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsAddTest {

    private static final String TAG = IpfsAddTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    private byte[] getRandomBytes(int number) {
        return RandomStringUtils.randomAlphabetic(number).getBytes();
    }

    @NonNull
    public File createCacheFile() throws IOException {
        return File.createTempFile("temp", ".io.ipfs.cid", context.getCacheDir());
    }

    @Test(expected = ClosedException.class)
    public void add_and_remove() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);
        String content = "Hallo dfsadf";
        Cid text = ipfs.storeText(content);
        assertNotNull(text);

        ipfs.rm(text);


        ipfs.getText(text, new TimeoutCloseable(10)); // closed exception expected


    }

    @Test
    public void add_dir() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);
        Cid dir = ipfs.createEmptyDir();
        assertNotNull(dir);

        String content = "Hallo";
        Cid text = ipfs.storeText(content);
        assertNotNull(text);

        byte[] data = ipfs.getData(text, new TimeoutCloseable(1));
        assertEquals(content, new String(data));

        dir = ipfs.addLinkToDir(dir, "text.txt", text);
        assertNotNull(dir);

        List<Link> links = ipfs.links(dir, new TimeoutCloseable(1));
        assertNotNull(links);
        assertEquals(links.size(), 1);

        dir = ipfs.rmLinkFromDir(dir, "text.txt");
        assertNotNull(dir);

        links = ipfs.links(dir, new TimeoutCloseable(1));
        assertNotNull(links);
        assertEquals(links.size(), 0);

    }

    @Test
    public void add_wrap_test() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        int packetSize = 1000;
        long maxData = 1000;
        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();

        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        Cid hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<Link> links = ipfs.ls(hash58Base, true, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 4);

        byte[] bytes = ipfs.getData(hash58Base, () -> false);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));


    }

    @Test
    public void add_dir_test() throws Exception {
        IPFS ipfs = TestEnv.getTestInstance(context);


        File inputFile = new File(context.getCacheDir(), UUID.randomUUID().toString());
        assertTrue(inputFile.createNewFile());
        for (int i = 0; i < 10; i++) {
            byte[] randomBytes = getRandomBytes(1000);
            FileServer.insertRecord(inputFile, i, 1000, randomBytes);
        }

        Cid hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<Link> links = ipfs.getLinks(hash58Base, () -> false);
        assertNotNull(links);


        assertEquals(links.size(), 0);
    }


    @Test
    public void add_test() throws Exception {

        int packetSize = 1000;
        long maxData = 1000;
        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();

        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        Cid hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<Link> links = ipfs.ls(hash58Base, true, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 4);
        assertNotEquals(links.get(0).getContent(), hash58Base);

        byte[] bytes = ipfs.getData(hash58Base, () -> false);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));

    }


    @Test
    public void add_wrap_small_test() throws Exception {

        int packetSize = 200;
        long maxData = 1000;
        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();


        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        Cid hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<Link> links = ipfs.getLinks(hash58Base, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 0);

        byte[] bytes = ipfs.getData(hash58Base, () -> false);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);


        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));


    }

    //@Test
    public void add_small_test() throws Exception {

        int packetSize = 200;
        long maxData = 1000;
        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();

        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        Cid hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);

        List<Link> links = ipfs.getLinks(hash58Base, () -> false);
        assertNotNull(links);
        assertEquals(links.size(), 0);

        byte[] bytes = ipfs.getData(hash58Base, () -> false);
        assertNotNull(bytes);
        assertEquals(bytes.length, size);

        IOUtils.contentEquals(new ByteArrayInputStream(bytes), new FileInputStream(inputFile));


    }
}
