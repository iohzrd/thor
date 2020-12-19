package threads.thor.bt.processor;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class TorrentContextFinalizer implements ContextFinalizer {

    private final TorrentRegistry torrentRegistry;
    private final EventSink eventSink;

    public TorrentContextFinalizer(TorrentRegistry torrentRegistry, EventSink eventSink) {
        this.torrentRegistry = torrentRegistry;
        this.eventSink = eventSink;
    }

    @Override
    public void finalizeContext(MagnetContext context) {
        torrentRegistry.getDescriptor(context.getTorrentId()).ifPresent(TorrentDescriptor::stop);
        eventSink.fireTorrentStopped(context.getTorrentId());
    }
}
