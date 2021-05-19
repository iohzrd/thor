package io.ipfs.host;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.LogUtils;
import io.ipfs.multiaddr.Multiaddr;
import io.ipfs.multiaddr.Protocol;


public class AddrInfo {
    private static final String TAG = AddrInfo.class.getSimpleName();
    @NonNull
    private final PeerId peerId;
    private final Set<Multiaddr> addresses = new HashSet<>();

    private AddrInfo(@NonNull PeerId id) {
        this.peerId = id;

    }

    public static AddrInfo create(@NonNull PeerId id, @NonNull Collection<Multiaddr> addresses) {
        AddrInfo addrInfo = new AddrInfo(id);
        for (Multiaddr ma : addresses) {
            addrInfo.addAddress(ma);
        }
        return addrInfo;
    }

    public static AddrInfo create(@NonNull PeerId id, @NonNull Multiaddr address) {
        AddrInfo addrInfo = new AddrInfo(id);
        addrInfo.addAddress(address);
        return addrInfo;
    }

    public static boolean isSupported(@NonNull Multiaddr address) {

        if (address.has(Protocol.Type.DNSADDR)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS4)) {
            return true;
        }
        if (address.has(Protocol.Type.DNS6)) {
            return true;
        }
        if (address.has(Protocol.Type.IP4)) {
            if (Objects.equals(address.getStringComponent(Protocol.Type.IP4), "127.0.0.1")) {
                return false;
            }
        }
        if (address.has(Protocol.Type.IP6)) {
            if (Objects.equals(address.getStringComponent(Protocol.Type.IP6), "::1")) {
                return false;
            }
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


    private void addAddress(@NonNull Multiaddr address) {
        if (isSupported(address)) {
            addresses.add(address);
        }
    }

    public boolean hasAddresses() {
        return !this.addresses.isEmpty();
    }
}
