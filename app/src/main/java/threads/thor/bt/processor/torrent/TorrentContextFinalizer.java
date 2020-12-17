package threads.thor.bt.processor.torrent;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.processor.ContextFinalizer;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class TorrentContextFinalizer<C extends TorrentContext> implements ContextFinalizer<C> {

    private final TorrentRegistry torrentRegistry;
    private final EventSink eventSink;

    public TorrentContextFinalizer(TorrentRegistry torrentRegistry, EventSink eventSink) {
        this.torrentRegistry = torrentRegistry;
        this.eventSink = eventSink;
    }

    @Override
    public void finalizeContext(C context) {
        torrentRegistry.getDescriptor(context.getTorrentId()).ifPresent(TorrentDescriptor::stop);
        eventSink.fireTorrentStopped(context.getTorrentId());
    }
}
