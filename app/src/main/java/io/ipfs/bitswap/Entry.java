package io.ipfs.bitswap;

import io.protos.bitswap.BitswapProtos;

public class Entry {
    public io.ipfs.cid.Cid Cid;
    public int Priority;
    public BitswapProtos.Message.Wantlist.WantType WantType;
}

