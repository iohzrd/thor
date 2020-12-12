package threads.thor.ipfs;

public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
