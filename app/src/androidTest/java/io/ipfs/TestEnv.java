package io.ipfs;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import io.LogUtils;

class TestEnv {
    private static final String TAG = TestEnv.class.getSimpleName();

    static boolean isConnected(@NonNull Context context) {

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        android.net.Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

    }

    public static IPFS getTestInstance(@NonNull Context context) {

        long time = System.currentTimeMillis();
        IPFS ipfs = IPFS.getInstance(context);

        if (!ipfs.isDaemonRunning()) {
            ipfs.bootstrap();
        }

        LogUtils.error(TAG, "Time Daemon : " + (System.currentTimeMillis() - time));


        return ipfs;
    }


}
