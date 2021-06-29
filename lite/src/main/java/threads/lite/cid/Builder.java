package threads.lite.cid;

public interface Builder {

    Cid sum(byte[] data);

    long getCodec();

    Builder withCodec(long codec);

}
