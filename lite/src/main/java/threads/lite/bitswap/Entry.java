package threads.lite.bitswap;

import bitswap.pb.MessageOuterClass;


public class Entry {
    public threads.lite.cid.Cid Cid;
    public int Priority;
    public MessageOuterClass.Message.Wantlist.WantType WantType;
}

