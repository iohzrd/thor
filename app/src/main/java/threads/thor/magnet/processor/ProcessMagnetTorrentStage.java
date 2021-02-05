package threads.thor.magnet.processor;

import threads.thor.magnet.event.EventSink;
import threads.thor.magnet.torrent.TorrentRegistry;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage {

    public ProcessMagnetTorrentStage(ProcessingStage next,
                                     TorrentRegistry torrentRegistry,
                                     EventSink eventSink) {
        super(next, torrentRegistry, eventSink);
    }
}
