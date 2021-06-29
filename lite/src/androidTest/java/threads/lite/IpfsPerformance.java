package threads.lite;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.cid.Cid;
import threads.lite.core.Progress;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class IpfsPerformance {
    private static final String TAG = IpfsPerformance.class.getSimpleName();
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

    @Test
    public void test_add_cat_small() throws Exception {

        int packetSize = 1000;
        long maxData = 100;

        IPFS ipfs = TestEnv.getTestInstance(context);


        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();


        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");
        long now = System.currentTimeMillis();
        Cid cid = ipfs.storeFile(inputFile);
        assertNotNull(cid);
        LogUtils.debug(TAG, "Add : " + cid +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");

        File file = createCacheFile();
        file.deleteOnExit();
        assertTrue(file.exists());
        assertTrue(file.delete());

        now = System.currentTimeMillis();

        byte[] data = ipfs.getData(cid, () -> false);
        Objects.requireNonNull(data);

        LogUtils.debug(TAG, "Cat : " + cid +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");

        assertEquals(data.length, size);

        File temp = createCacheFile();
        ipfs.storeToFile(temp, cid, () -> false);

        assertEquals(temp.length(), size);

        assertTrue(temp.delete());
        assertTrue(inputFile.delete());


        ipfs.rm(cid);


    }


    @Test
    public void test_cmp_files() throws Exception {

        int packetSize = 10000;
        long maxData = 5000;


        IPFS ipfs = TestEnv.getTestInstance(context);

        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }
        long size = inputFile.length();


        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        Cid cid = ipfs.storeFile(inputFile);
        assertNotNull(cid);
        File file = createCacheFile();
        ipfs.storeToFile(file, cid, () -> false);

        assertEquals(file.length(), size);

        assertTrue(FileUtils.contentEquals(inputFile, file));

        assertTrue(file.delete());
        assertTrue(inputFile.delete());


        ipfs.rm(cid);


    }

    @Test
    public void test_add_cat() throws Exception {


        IPFS ipfs = TestEnv.getTestInstance(context);

        int packetSize = 10000;
        long maxData = 10000;


        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }

        long size = inputFile.length();
        assertEquals(packetSize * maxData, size);

        LogUtils.debug(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");


        long now = System.currentTimeMillis();
        Cid cid = ipfs.storeFile(inputFile);
        assertNotNull(cid);
        LogUtils.debug(TAG, "Add : " + cid +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");


        File temp = createCacheFile();
        ipfs.storeToFile(temp, cid, new Progress() {
            @Override
            public void setProgress(int percent) {
                LogUtils.debug(TAG, "Progress : " + percent);
            }

            @Override
            public boolean doProgress() {
                return true;
            }

            @Override
            public boolean isClosed() {
                return false;
            }

        });

        assertEquals(temp.length(), size);


        File abort = createCacheFile();
        AtomicBoolean closed = new AtomicBoolean(false);
        try {
            ipfs.storeToFile(abort, cid, new Progress() {
                @Override
                public void setProgress(int percent) {
                    if (percent > 50) {
                        closed.set(true);
                    }
                    LogUtils.debug(TAG, "Progress : " + percent);
                }

                @Override
                public boolean doProgress() {
                    return true;
                }

                @Override
                public boolean isClosed() {
                    return closed.get();
                }

            });
            fail();
        } catch (Throwable ignore) {
            //
        }


        now = System.currentTimeMillis();
        Cid hash58Base_2 = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base_2);
        LogUtils.debug(TAG, "Add : " + hash58Base_2 +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");


        now = System.currentTimeMillis();
        File outputFile1 = createCacheFile();
        ipfs.storeToFile(outputFile1, cid, () -> false);
        LogUtils.debug(TAG, "Cat : " + cid +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");


        now = System.currentTimeMillis();
        File outputFile2 = createCacheFile();
        ipfs.storeToFile(outputFile2, cid, () -> false);
        LogUtils.debug(TAG, "Cat : " + cid +
                " Time : " + ((System.currentTimeMillis() - now) / 1000) + "[s]");


        assertEquals(outputFile1.length(), size);
        assertEquals(outputFile2.length(), size);
        assertTrue(outputFile2.delete());
        assertTrue(outputFile1.delete());
        assertTrue(inputFile.delete());

        ipfs.rm(cid);


    }


}
