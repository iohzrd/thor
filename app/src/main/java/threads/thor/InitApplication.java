package threads.thor;

import android.app.Application;

import androidx.annotation.NonNull;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import threads.lite.IPFS;
import threads.lite.cid.PeerId;
import threads.LogUtils;
import threads.thor.core.Content;
import threads.thor.core.pages.PAGES;
import threads.thor.utils.AdBlocker;

public class InitApplication extends Application {

    public static final String TIME_TAG = "TIME_TAG";
    private static final String TAG = InitApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        long start = System.currentTimeMillis();

        AdBlocker.init(getApplicationContext());


        LogUtils.info(TIME_TAG, "InitApplication after add blocker [" +
                (System.currentTimeMillis() - start) + "]...");
        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            ipfs.setPusher((peerId, content) -> {
                try {
                    onMessageReceived(peerId, content);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            });
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        LogUtils.info(TIME_TAG, "InitApplication after starting ipfs [" +
                (System.currentTimeMillis() - start) + "]...");

    }
    private final Gson gson = new Gson();

    public void onMessageReceived(@NonNull PeerId peerId, @NonNull String content) {

        try {
            Type hashMap = new TypeToken<HashMap<String, String>>() {}.getType();

            Objects.requireNonNull(peerId);
            IPFS ipfs = IPFS.getInstance(getApplicationContext());

            Objects.requireNonNull(content);
            Map<String, String> data = gson.fromJson(content, hashMap);

            LogUtils.debug(TAG, "Push Message : " + data.toString());


            String ipns = data.get(Content.IPNS);
            Objects.requireNonNull(ipns);
            String seq = data.get(Content.SEQ);
            Objects.requireNonNull(seq);

            long sequence = Long.parseLong(seq);
            if (sequence >= 0) {
                if (ipfs.isValidCID(ipns)) {
                    PAGES pages = PAGES.getInstance(getApplicationContext());
                    pages.setPageSequence(peerId.toBase58(), sequence);
                    pages.setPageContent(peerId.toBase58(), ipns);
                }
            }


        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
