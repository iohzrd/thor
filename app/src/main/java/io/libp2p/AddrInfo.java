package io.libp2p;

import androidx.annotation.NonNull;

import com.google.common.collect.Iterables;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.LogUtils;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;


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
        if (address.has(Protocol.WS)) {
            LogUtils.info(TAG, "WS " + address.toString()); // maybe TODO
            return;
        }
        if (address.has(Protocol.WSS)) {
            LogUtils.info(TAG, "WSS " + address.toString()); // maybe TODO
            return;
        }
        if (address.has(Protocol.P2PCIRCUIT)) { // TODO SUPPORT THIS
            LogUtils.info(TAG, "P2PCIRCUIT " + address.toString());
            return;
        }
        if (address.has(Protocol.QUIC)) { // TODO SUPPORT THIS
            LogUtils.info(TAG, "QUIC " + address.toString());
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
        this.addresses.add(address);
    }

    public boolean hasAddresses() {
        return !this.addresses.isEmpty();
    }
}
