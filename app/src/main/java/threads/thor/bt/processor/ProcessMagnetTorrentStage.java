package threads.thor.bt.processor;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.torrent.TorrentRegistry;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage {

    public ProcessMagnetTorrentStage(ProcessingStage next,
                                     TorrentRegistry torrentRegistry,
                                     EventSink eventSink) {
        super(next, torrentRegistry, eventSink);
    }
}
