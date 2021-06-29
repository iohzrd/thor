package threads.thor.services;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import threads.LogUtils;


public class RegistrationService implements NsdManager.RegistrationListener {
    private static final String TAG = RegistrationService.class.getSimpleName();
    private static RegistrationService INSTANCE = null;

    public static RegistrationService getInstance() {
        if (INSTANCE == null) {

            synchronized (RegistrationService.class) {

                if (INSTANCE == null) {
                    INSTANCE = new RegistrationService();
                }
            }

        }
        return INSTANCE;
    }

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        LogUtils.error(TAG, "RegistrationFailed : " + errorCode);
    }

    @Override
    public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        LogUtils.error(TAG, "Un-RegistrationFailed : " + errorCode);
    }

    @Override
    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
        LogUtils.info(TAG, "ServiceRegistered : " + serviceInfo.getServiceName());
    }

    @Override
    public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
        LogUtils.info(TAG, "Un-ServiceRegistered : " + serviceInfo.getServiceName());
    }
}
