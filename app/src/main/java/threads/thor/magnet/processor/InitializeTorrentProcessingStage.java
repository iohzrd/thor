package threads.thor.magnet.processor;

import java.util.Objects;

import threads.thor.magnet.data.Bitfield;
import threads.thor.magnet.event.EventSink;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.PeerConnectionPool;
import threads.thor.magnet.net.extended.ExtendedHandshakeConsumer;
import threads.thor.magnet.net.pipeline.BufferedPieceRegistry;
import threads.thor.magnet.torrent.BitfieldConsumer;
import threads.thor.magnet.torrent.DataWorker;
import threads.thor.magnet.torrent.GenericConsumer;
import threads.thor.magnet.torrent.MetadataProducer;
import threads.thor.magnet.torrent.PeerRequestConsumer;
import threads.thor.magnet.torrent.PieceConsumer;
import threads.thor.magnet.torrent.PieceStatistics;
import threads.thor.magnet.torrent.RequestProducer;
import threads.thor.magnet.torrent.TorrentDescriptor;
import threads.thor.magnet.torrent.TorrentRegistry;

public class InitializeTorrentProcessingStage extends TerminateOnErrorProcessingStage {

    private final PeerConnectionPool connectionPool;
    private final TorrentRegistry torrentRegistry;
    private final DataWorker dataWorker;
    private final BufferedPieceRegistry bufferedPieceRegistry;
    private final EventSink eventSink;


    public InitializeTorrentProcessingStage(ProcessingStage next,
                                            PeerConnectionPool connectionPool,
                                            TorrentRegistry torrentRegistry,
                                            DataWorker dataWorker,
                                            BufferedPieceRegistry bufferedPieceRegistry,
                                            EventSink eventSink) {
        super(next);
        this.connectionPool = connectionPool;
        this.torrentRegistry = torrentRegistry;
        this.dataWorker = dataWorker;
        this.bufferedPieceRegistry = bufferedPieceRegistry;
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        Torrent torrent = context.getTorrent();
        Objects.requireNonNull(torrent);
        TorrentDescriptor descriptor = torrentRegistry.register(torrent, context.getStorage());

        TorrentId torrentId = torrent.getTorrentId();
        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        PieceStatistics pieceStatistics = createPieceStatistics(bitfield);

        context.getRouter().registerMessagingAgent(GenericConsumer.consumer());
        context.getRouter().registerMessagingAgent(new BitfieldConsumer(bitfield, pieceStatistics, eventSink));
        context.getRouter().registerMessagingAgent(new ExtendedHandshakeConsumer(connectionPool));
        context.getRouter().registerMessagingAgent(new PieceConsumer(torrentId, bitfield, dataWorker, bufferedPieceRegistry, eventSink));
        context.getRouter().registerMessagingAgent(new PeerRequestConsumer(torrentId, dataWorker));
        context.getRouter().registerMessagingAgent(new RequestProducer(descriptor.getDataDescriptor()));
        context.getRouter().registerMessagingAgent(new MetadataProducer(context.getTorrent()));

        context.setBitfield(bitfield);
        context.setPieceStatistics(pieceStatistics);
    }

    private PieceStatistics createPieceStatistics(Bitfield bitfield) {
        return new PieceStatistics(bitfield);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
