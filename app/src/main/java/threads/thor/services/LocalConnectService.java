package threads.thor.services;

import android.content.Context;

import androidx.annotation.NonNull;

import threads.LogUtils;
import threads.lite.IPFS;
import threads.lite.cid.PeerId;

public class LocalConnectService {

    private static final String TAG = LocalConnectService.class.getSimpleName();

    public static void connect(@NonNull Context context, @NonNull String pid,
                               @NonNull String host, int port, boolean inet6) {

        try {
            IPFS ipfs = IPFS.getInstance(context);

            ipfs.swarmEnhance(PeerId.fromBase58(pid));

            String pre = "/ip4";
            if (inet6) {
                pre = "/ip6";
            }
            String multiAddress = pre + host + "/udp/" + port + "/quic/p2p/" + pid;

            boolean connect = ipfs.swarmConnect(multiAddress, IPFS.CONNECT_TIMEOUT);


            LogUtils.error(TAG,"Success " + connect + " " + pid);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

}

