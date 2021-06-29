package threads.lite.host;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import threads.lite.LogUtils;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.cid.Protocol;


public class AddrInfo {
    private static final String TAG = AddrInfo.class.getSimpleName();
    @NonNull
    private final PeerId peerId;
    private final Set<Multiaddr> addresses = new HashSet<>();

    private AddrInfo(@NonNull PeerId id) {
        this.peerId = id;

    }

    public static AddrInfo create(@NonNull PeerId id, @NonNull Collection<Multiaddr> addresses,
                                  boolean acceptSiteLocal) {
        AddrInfo addrInfo = new AddrInfo(id);
        for (Multiaddr ma : addresses) {
            addrInfo.addAddress(ma, acceptSiteLocal);
        }
        return addrInfo;
    }

    public static AddrInfo create(@NonNull PeerId id, @NonNull Multiaddr address,
                                  boolean acceptSiteLocal) {
        AddrInfo addrInfo = new AddrInfo(id);
        addrInfo.addAddress(address, acceptSiteLocal);
        return addrInfo;
    }

    public static boolean isSupported(@NonNull Multiaddr address, boolean acceptLocal) {

        if (address.has(Protocol.Type.DNSADDR)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS4)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS6)) {
            return true;
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(address.getHost());
            if (inetAddress.isAnyLocalAddress() || inetAddress.isLinkLocalAddress()
                    || (!acceptLocal && inetAddress.isLoopbackAddress())
                    || (!acceptLocal && inetAddress.isSiteLocalAddress())) {
                LogUtils.info(TAG, "Not supported " + address.toString());
                return false;
            }
        } catch (Throwable throwable) {
            LogUtils.debug(TAG, "" + throwable);
            return false;
        }

        if (address.has(Protocol.Type.QUIC)) {
            return true;
        } else {
            LogUtils.info(TAG, "Not supported " + address.toString());
            return false;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "AddrInfo{" +
                "peerId=" + peerId +
                ", addresses=" + addresses +
                '}';
    }

    @NonNull
    public PeerId getPeerId() {
        return peerId;
    }

    public Multiaddr[] getAddresses() {
        return Iterables.toArray(addresses, Multiaddr.class);
    }

    public Set<Multiaddr> asSet() {
        return addresses;
    }


    private void addAddress(@NonNull Multiaddr address, boolean acceptSiteLocal) {
        if (isSupported(address, acceptSiteLocal)) {
            addresses.add(address);
        }
    }

    public boolean hasAddresses() {
        return !this.addresses.isEmpty();
    }
}
