package io.ipfs;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

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

        IPFS ipfs = IPFS.getInstance(context);
        ipfs.clearDatabase();
        ipfs.bootstrap();

        return ipfs;
    }


}
