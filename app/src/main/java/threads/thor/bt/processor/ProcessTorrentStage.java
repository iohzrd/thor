package threads.thor.bt.processor;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class ProcessTorrentStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final TorrentRegistry torrentRegistry;

    private final EventSink eventSink;

    public ProcessTorrentStage(ProcessingStage<C> next,
                               TorrentRegistry torrentRegistry,
                               EventSink eventSink) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(C context) {
        TorrentId torrentId = context.getTorrentId();
        TorrentDescriptor descriptor = getDescriptor(torrentId);

        descriptor.start();

        eventSink.fireTorrentStarted(torrentId);

        while (descriptor.isActive()) {
            try {
                Thread.sleep(1000);
                if (context.getState().getPiecesRemaining() == 0) {
                    break;
                }
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
        return ProcessingEvent.DOWNLOAD_COMPLETE;
    }
}
