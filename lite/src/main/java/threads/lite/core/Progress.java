package threads.lite.core;


public interface Progress extends Closeable {

    void setProgress(int progress);

    boolean doProgress();

}
