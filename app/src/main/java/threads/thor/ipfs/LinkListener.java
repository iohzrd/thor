package threads.thor.ipfs;

public interface LinkListener extends Closeable {
    void link(String name, String hash, long size, int type);
}
