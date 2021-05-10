package threads.thor.services;

import android.content.Context;

import androidx.annotation.NonNull;

import io.LogUtils;
import io.ipfs.IPFS;
import io.ipfs.host.PeerInfo;
import io.ipfs.core.TimeoutCloseable;
import io.libp2p.core.PeerId;

public class LocalConnectService {

    private static final String TAG = LocalConnectService.class.getSimpleName();

    public static void connect(@NonNull Context context, @NonNull String pid,
                               @NonNull String host, int port, boolean inet6) {

        try {
            IPFS ipfs = IPFS.getInstance(context);

            String pre = "/ip4";
            if (inet6) {
                pre = "/ip6";
            }
            String multiAddress = pre + host + "/udp/" + port + "/quic/p2p/" + pid;



            boolean connect = ipfs.swarmConnect(multiAddress, 10);

            LogUtils.error(TAG, "Success ? " + connect + " for " + multiAddress);

            PeerInfo info = ipfs.getPeerInfo(PeerId.fromBase58(pid), new TimeoutCloseable(10));

            LogUtils.error(TAG, info.toString());


            ipfs.notify(PeerId.fromBase58(pid), "Moin");

        } catch (Throwable throwable){
            LogUtils.error(TAG, throwable);
        }
    }

}

