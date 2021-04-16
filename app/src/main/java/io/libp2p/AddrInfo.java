package io.libp2p;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.LogUtils;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;


public class AddrInfo {
    private static final String TAG = AddrInfo.class.getSimpleName();
    @NonNull
    private final PeerId peerId;
    private final List<Multiaddr> addresses = new ArrayList<>();

    private AddrInfo(@NonNull PeerId id) {
        this.peerId = id;

    }

    public static AddrInfo create(@NonNull PeerId id, @NonNull Collection<Multiaddr> addresses) {
        AddrInfo addrInfo = new AddrInfo(id);
        for (Multiaddr ma:addresses) {
            addrInfo.addAddress(ma);
        }
        return addrInfo;
    }

    public static AddrInfo create(@NonNull PeerId id, @NonNull Multiaddr address) {
        AddrInfo addrInfo = new AddrInfo(id);
        addrInfo.addAddress(address);
        return addrInfo;
    }

    @NotNull
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


    private void addAddress(@NonNull Multiaddr address) {
        if (address.has(Protocol.DNS6)) {
            LogUtils.info(TAG, "Filter out DNS6 " + address.toString()); // maybe TODO
            // return;
        }
        if (address.has(Protocol.DNSADDR)) {
            LogUtils.info(TAG, "Filter out DNS6 " + address.toString()); // TODO
            // return;
        }
        if (address.has(Protocol.WS)) {
            LogUtils.info(TAG, "Filter out WS " + address.toString()); // maybe TODO
            return;
        }
        if (address.has(Protocol.DNS4)) {
            LogUtils.info(TAG, "Filter out DNS4 " + address.toString()); // maybe TODO
            // return;
        }
        if (address.has(Protocol.P2PCIRCUIT)) { // TODO SUPPORT THIS
            LogUtils.info(TAG, "Filter out P2PCIRCUIT " + address.toString());
            return;
        }
        if (address.has(Protocol.QUIC)) { // TODO SUPPORT THIS
            LogUtils.info(TAG, "Filter out QUIC " + address.toString());
            return;
        }
        if (address.has(Protocol.IP4)) {
            if (Objects.equals(address.getStringComponent(Protocol.IP4), "127.0.0.1")) { // TODO
                return;
            }
        }
        if (address.has(Protocol.IP6)) {
            if (Objects.equals(address.getStringComponent(Protocol.IP6), "::1")) { // TODO
                return;
            }
        }
        if (!this.addresses.contains(address)) {
            this.addresses.add(address);
        }
    }

    public boolean hasAddresses() {
        return !this.addresses.isEmpty();
    }
}
