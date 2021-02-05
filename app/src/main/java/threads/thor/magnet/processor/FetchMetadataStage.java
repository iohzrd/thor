package threads.thor.magnet.processor;

import threads.thor.magnet.event.EventSink;
import threads.thor.magnet.metainfo.MetadataService;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.InetPeer;
import threads.thor.magnet.peer.PeerRegistry;
import threads.thor.magnet.torrent.BitfieldCollectingConsumer;
import threads.thor.magnet.torrent.MetadataConsumer;
import threads.thor.magnet.torrent.TorrentDescriptor;
import threads.thor.magnet.torrent.TorrentRegistry;


public class FetchMetadataStage extends TerminateOnErrorProcessingStage {

    private final MetadataService metadataService;
    private final TorrentRegistry torrentRegistry;
    private final PeerRegistry peerRegistry;
    private final EventSink eventSink;

    public FetchMetadataStage(ProcessingStage next,
                              TorrentRegistry torrentRegistry,
                              PeerRegistry peerRegistry,
                              EventSink eventSink) {
        super(next);
        this.metadataService = new MetadataService();
        this.torrentRegistry = torrentRegistry;
        this.peerRegistry = peerRegistry;
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        TorrentId torrentId = context.getMagnetUri().getTorrentId();

        MetadataConsumer metadataConsumer = new MetadataConsumer(metadataService, torrentId);
        context.getRouter().registerMessagingAgent(metadataConsumer);

        // need to also receive Bitfields and Haves (without validation for the number of pieces...)
        BitfieldCollectingConsumer bitfieldConsumer = new BitfieldCollectingConsumer();
        context.getRouter().registerMessagingAgent(bitfieldConsumer);

        getDescriptor(torrentId).start();

        context.getMagnetUri().getPeerAddresses().forEach(peerAddress ->
                peerRegistry.addPeer(torrentId, InetPeer.build(peerAddress)));


        Torrent torrent = metadataConsumer.waitForTorrent();


        context.setTorrent(torrent);
        eventSink.fireMetadataAvailable(torrentId);

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
