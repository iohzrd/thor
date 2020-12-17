package threads.thor.bt.processor;

import threads.thor.bt.Config;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.IPeerConnectionPool;
import threads.thor.bt.net.extended.ExtendedHandshakeConsumer;
import threads.thor.bt.net.pipeline.IBufferedPieceRegistry;
import threads.thor.bt.torrent.BitfieldConsumer;
import threads.thor.bt.torrent.DataWorker;
import threads.thor.bt.torrent.GenericConsumer;
import threads.thor.bt.torrent.MetadataProducer;
import threads.thor.bt.torrent.PeerRequestConsumer;
import threads.thor.bt.torrent.PieceConsumer;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.RequestProducer;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class InitializeTorrentProcessingStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final IPeerConnectionPool connectionPool;
    private final TorrentRegistry torrentRegistry;
    private final DataWorker dataWorker;
    private final IBufferedPieceRegistry bufferedPieceRegistry;
    private final EventSink eventSink;
    private final Config config;

    public InitializeTorrentProcessingStage(ProcessingStage<C> next,
                                            IPeerConnectionPool connectionPool,
                                            TorrentRegistry torrentRegistry,
                                            DataWorker dataWorker,
                                            IBufferedPieceRegistry bufferedPieceRegistry,
                                            EventSink eventSink,
                                            Config config) {
        super(next);
        this.connectionPool = connectionPool;
        this.torrentRegistry = torrentRegistry;
        this.dataWorker = dataWorker;
        this.bufferedPieceRegistry = bufferedPieceRegistry;
        this.eventSink = eventSink;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        Torrent torrent = context.getTorrent();
        TorrentDescriptor descriptor = torrentRegistry.register(torrent, context.getStorage());

        TorrentId torrentId = torrent.getTorrentId();
        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        PieceStatistics pieceStatistics = createPieceStatistics(bitfield);

        context.getRouter().registerMessagingAgent(GenericConsumer.consumer());
        context.getRouter().registerMessagingAgent(new BitfieldConsumer(bitfield, pieceStatistics, eventSink));
        context.getRouter().registerMessagingAgent(new ExtendedHandshakeConsumer(connectionPool));
        context.getRouter().registerMessagingAgent(new PieceConsumer(torrentId, bitfield, dataWorker, bufferedPieceRegistry, eventSink));
        context.getRouter().registerMessagingAgent(new PeerRequestConsumer(torrentId, dataWorker));
        context.getRouter().registerMessagingAgent(new RequestProducer(descriptor.getDataDescriptor(), config.getMaxOutstandingRequests()));
        context.getRouter().registerMessagingAgent(new MetadataProducer(context.getTorrent(), config));

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
