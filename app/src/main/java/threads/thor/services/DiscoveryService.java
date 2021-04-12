package threads.thor.services;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import androidx.annotation.NonNull;

public class DiscoveryService implements NsdManager.DiscoveryListener {

    private static DiscoveryService INSTANCE = null;
    private OnServiceFoundListener mListener = null;

    public static DiscoveryService getInstance() {
        if (INSTANCE == null) {

            synchronized (DiscoveryService.class) {

                if (INSTANCE == null) {
                    INSTANCE = new DiscoveryService();
                }
            }

        }
        return INSTANCE;
    }

    public void setOnServiceFoundListener(@NonNull OnServiceFoundListener listener) {

        mListener = listener;
    }

    @Override
    public void onStartDiscoveryFailed(String serviceType, int errorCode) {

    }

    @Override
    public void onStopDiscoveryFailed(String serviceType, int errorCode) {

    }

    @Override
    public void onDiscoveryStarted(String serviceType) {
    }

    @Override
    public void onDiscoveryStopped(String serviceType) {
    }

    @Override
    public void onServiceFound(NsdServiceInfo serviceInfo) {

        if (mListener != null) {
            mListener.resolveService(serviceInfo);
        }
    }

    @Override
    public void onServiceLost(NsdServiceInfo serviceInfo) {
    }

    public interface OnServiceFoundListener {

        void resolveService(@NonNull NsdServiceInfo serviceInfo);
    }
}
