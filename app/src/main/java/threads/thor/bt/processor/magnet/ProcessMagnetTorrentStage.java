package threads.thor.bt.processor.magnet;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.torrent.ProcessTorrentStage;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.tracker.ITrackerService;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage<MagnetContext> {

    public ProcessMagnetTorrentStage(ProcessingStage<MagnetContext> next,
                                     TorrentRegistry torrentRegistry,
                                     ITrackerService trackerService,
                                     EventSink eventSink) {
        super(next, torrentRegistry, trackerService, eventSink);
    }

    @Override
    protected void onStarted(MagnetContext context) {

    }
}
