package threads.thor.bt.processor;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class SeedStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final TorrentRegistry torrentRegistry;

    public SeedStage(ProcessingStage<C> next,
                     TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId());

        while (descriptor.isActive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted", e);
            }
        }
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for threads.torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
