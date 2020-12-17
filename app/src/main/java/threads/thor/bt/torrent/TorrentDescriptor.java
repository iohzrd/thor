package threads.thor.bt.torrent;

import threads.thor.bt.data.DataDescriptor;

public final class TorrentDescriptor {

    // !! this can be null in case with magnets (and in the beginning of processing) !!
    private volatile DataDescriptor dataDescriptor;

    private volatile boolean active;

    TorrentDescriptor() {
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void start() {
        active = true;
    }


    public synchronized void stop() {
        active = false;
    }

    public void complete() {
        // do nothing
        // TODO: should this be deprecated in TorrentDescriptor interface?
    }


    public DataDescriptor getDataDescriptor() {
        return dataDescriptor;
    }

    void setDataDescriptor(DataDescriptor dataDescriptor) {
        this.dataDescriptor = dataDescriptor;
    }
}
