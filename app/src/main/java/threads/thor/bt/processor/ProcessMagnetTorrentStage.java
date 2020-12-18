package threads.thor.bt.processor;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.torrent.TorrentRegistry;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage<MagnetContext> {

    public ProcessMagnetTorrentStage(ProcessingStage<MagnetContext> next,
                                     TorrentRegistry torrentRegistry,
                                     EventSink eventSink) {
        super(next, torrentRegistry, eventSink);
    }
}
