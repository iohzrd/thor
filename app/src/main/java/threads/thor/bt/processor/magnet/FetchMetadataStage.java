package threads.thor.bt.processor.magnet;

import android.content.Context;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.MetadataService;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.InetPeer;
import threads.thor.bt.peer.PeerRegistry;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.TerminateOnErrorProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.messaging.BitfieldCollectingConsumer;
import threads.thor.bt.torrent.messaging.MetadataConsumer;
import threads.thor.bt.tracker.AnnounceKey;

public class FetchMetadataStage extends TerminateOnErrorProcessingStage<MagnetContext> {

    private final MetadataService metadataService;
    private final TorrentRegistry torrentRegistry;
    private final PeerRegistry peerRegistry;
    private final EventSink eventSink;
    private final Config config;

    public FetchMetadataStage(ProcessingStage<MagnetContext> next,
                              TorrentRegistry torrentRegistry,
                              PeerRegistry peerRegistry,
                              EventSink eventSink,
                              Config config,
                              Context context) {
        super(next);
        this.metadataService = new MetadataService(context);
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.eventSink = eventSink;
        this.config = config;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        TorrentId torrentId = context.getMagnetUri().getTorrentId();

        MetadataConsumer metadataConsumer = new MetadataConsumer(metadataService, torrentId, config);
        context.getRouter().registerMessagingAgent(metadataConsumer);

        // need to also receive Bitfields and Haves (without validation for the number of pieces...)
        BitfieldCollectingConsumer bitfieldConsumer = new BitfieldCollectingConsumer();
        context.getRouter().registerMessagingAgent(bitfieldConsumer);

        getDescriptor(torrentId).start();

        context.getMagnetUri().getPeerAddresses().forEach(peerAddress ->
                peerRegistry.addPeer(torrentId, InetPeer.build(peerAddress)));

        context.getMagnetUri().getTrackerUrls().forEach(trackerUrl -> {
            // TODO: should we use a single multi-key instead, containing all trackers from the magnet link?
            peerRegistry.addPeerSource(torrentId, new AnnounceKey(trackerUrl));
        });


        Torrent torrent = metadataConsumer.waitForTorrent();


        context.setTorrent(torrent);
        eventSink.fireMetadataAvailable(torrentId, torrent);

        context.setBitfieldConsumer(bitfieldConsumer);
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for threads.torrent ID: " + torrentId));
    }


    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.TORRENT_FETCHED;
    }
}
