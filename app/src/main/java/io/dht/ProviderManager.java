package io.dht;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.ipfs.cid.Cid;
import io.libp2p.core.PeerId;


public class ProviderManager {
    private final ConcurrentHashMap<Cid, HashSet<PeerId>> providers = new ConcurrentHashMap<>();


    @NonNull
    public Set<PeerId> GetProviders(@NonNull Cid cid) {
        HashSet<PeerId> res = providers.get(cid);
        if (res == null) {
            return Collections.emptySet();
        }
        return res;
    }


}
