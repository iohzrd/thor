package io.ipfs.bitswap;

import bitswap.pb.MessageOuterClass;


public class Entry {
    public io.ipfs.cid.Cid Cid;
    public int Priority;
    public MessageOuterClass.Message.Wantlist.WantType WantType;
}

