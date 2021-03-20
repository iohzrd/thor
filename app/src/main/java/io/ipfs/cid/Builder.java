package io.ipfs.cid;

public interface Builder {

    Cid Sum(byte[] data);

    long GetCodec();

    Builder WithCodec(long codec);

}
