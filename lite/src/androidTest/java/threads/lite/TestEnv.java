package threads.lite;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

class TestEnv {

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
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.Network network = connectivityManager.getActiveNetwork();
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);

        if(linkProperties != null) {
            String interfaceName = linkProperties.getInterfaceName();
            ipfs.updateNetwork(interfaceName);
        }

        ipfs.clearDatabase();
        ipfs.bootstrap();
        ipfs.reset();

        System.gc();
        return ipfs;
    }


}
