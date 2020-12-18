package threads.thor.bt.processor;

import java.util.Set;
import java.util.function.Supplier;

import threads.thor.bt.Config;
import threads.thor.bt.IAgent;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionSource;
import threads.thor.bt.net.MessageDispatcher;
import threads.thor.bt.torrent.Assignments;
import threads.thor.bt.torrent.DefaultMessageRouter;
import threads.thor.bt.torrent.MessageRouter;
import threads.thor.bt.torrent.PeerWorkerFactory;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.TorrentSessionState;
import threads.thor.bt.torrent.TorrentWorker;

public class CreateSessionStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final TorrentRegistry torrentRegistry;
    private final EventSource eventSource;
    private final ConnectionSource connectionSource;
    private final MessageDispatcher messageDispatcher;
    private final Set<IAgent> messagingAgents;
    private final Config config;

    public CreateSessionStage(ProcessingStage<C> next,
                              TorrentRegistry torrentRegistry,
                              EventSource eventSource,
                              ConnectionSource connectionSource,
                              MessageDispatcher messageDispatcher,
                              Set<IAgent> messagingAgents,
                              Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.eventSource = eventSource;
        this.connectionSource = connectionSource;
        this.messageDispatcher = messageDispatcher;
        this.messagingAgents = messagingAgents;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        TorrentId torrentId = context.getTorrentId();
        TorrentDescriptor descriptor = torrentRegistry.register(torrentId);

        MessageRouter router = new DefaultMessageRouter(messagingAgents);
        PeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(router);

        Supplier<Bitfield> bitfieldSupplier = context::getBitfield;
        Supplier<Assignments> assignmentsSupplier = context::getAssignments;
        Supplier<PieceStatistics> statisticsSupplier = context::getPieceStatistics;


        new TorrentWorker(torrentId, messageDispatcher,
                connectionSource, peerWorkerFactory,
                bitfieldSupplier, assignmentsSupplier, statisticsSupplier, eventSource, config);

        context.setState(new TorrentSessionState(descriptor));
        context.setRouter(router);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
