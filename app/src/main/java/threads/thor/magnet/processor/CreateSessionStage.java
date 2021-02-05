package threads.thor.magnet.processor;

import java.util.Set;
import java.util.function.Supplier;

import threads.thor.magnet.IAgent;
import threads.thor.magnet.data.Bitfield;
import threads.thor.magnet.event.EventSource;
import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.ConnectionSource;
import threads.thor.magnet.net.MessageDispatcher;
import threads.thor.magnet.torrent.Assignments;
import threads.thor.magnet.torrent.DefaultMessageRouter;
import threads.thor.magnet.torrent.MessageRouter;
import threads.thor.magnet.torrent.PeerWorkerFactory;
import threads.thor.magnet.torrent.PieceStatistics;
import threads.thor.magnet.torrent.TorrentDescriptor;
import threads.thor.magnet.torrent.TorrentRegistry;
import threads.thor.magnet.torrent.TorrentSessionState;
import threads.thor.magnet.torrent.TorrentWorker;

public class CreateSessionStage extends TerminateOnErrorProcessingStage {

    private final TorrentRegistry torrentRegistry;
    private final EventSource eventSource;
    private final ConnectionSource connectionSource;
    private final MessageDispatcher messageDispatcher;
    private final Set<IAgent> messagingAgents;


    public CreateSessionStage(ProcessingStage next,
                              TorrentRegistry torrentRegistry,
                              EventSource eventSource,
                              ConnectionSource connectionSource,
                              MessageDispatcher messageDispatcher,
                              Set<IAgent> messagingAgents) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.eventSource = eventSource;
        this.connectionSource = connectionSource;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        TorrentId torrentId = context.getTorrentId();
        TorrentDescriptor descriptor = torrentRegistry.register(torrentId);

        MessageRouter router = new DefaultMessageRouter(messagingAgents);
        PeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(router);

        Supplier<Bitfield> bitfieldSupplier = context::getBitfield;
        Supplier<Assignments> assignmentsSupplier = context::getAssignments;
        Supplier<PieceStatistics> statisticsSupplier = context::getPieceStatistics;


        new TorrentWorker(torrentId, messageDispatcher,
                connectionSource, peerWorkerFactory,
                bitfieldSupplier, assignmentsSupplier, statisticsSupplier, eventSource);

        context.setState(new TorrentSessionState(descriptor));
        context.setRouter(router);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
