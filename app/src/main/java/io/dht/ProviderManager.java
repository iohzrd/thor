package io.dht;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.ipfs.cid.Cid;
import io.libp2p.AddrInfo;


public class ProviderManager {
    private final ConcurrentHashMap<Cid, Set<AddrInfo>> providers = new ConcurrentHashMap<>();


    @NonNull
    public Set<AddrInfo> GetProviders(@NonNull Cid cid) {
        Set<AddrInfo> res = providers.get(cid);
        if (res == null) {
            return Collections.emptySet();
        }
        return res;
    }


    public void addProvider(@NonNull Cid cid, @NonNull AddrInfo prov) {
        Set<AddrInfo> info = providers.get(cid);
        if (info == null) {
            info = new HashSet<>();
            info.add(prov);
            providers.put(cid, info);
        } else {
            // not correct should be done a merge of addresses
            info.add(prov);
        }
    }
}
