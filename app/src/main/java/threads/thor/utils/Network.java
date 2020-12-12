package threads.thor.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

public class Network {

    public static boolean isConnected(@NonNull Context context) {

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        android.net.Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

    }
}
