package threads.thor.bt.processor;

import java.util.Set;
import java.util.function.Supplier;

import threads.thor.bt.Config;
import threads.thor.bt.IAgent;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.IConnectionSource;
import threads.thor.bt.net.IMessageDispatcher;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.TorrentSessionState;
import threads.thor.bt.torrent.messaging.Assignments;
import threads.thor.bt.torrent.messaging.DefaultMessageRouter;
import threads.thor.bt.torrent.messaging.IPeerWorkerFactory;
import threads.thor.bt.torrent.messaging.MessageRouter;
import threads.thor.bt.torrent.messaging.PeerWorkerFactory;
import threads.thor.bt.torrent.messaging.TorrentWorker;

public class CreateSessionStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final TorrentRegistry torrentRegistry;
    private final EventSource eventSource;
    private final IConnectionSource connectionSource;
    private final IMessageDispatcher messageDispatcher;
    private final Set<IAgent> messagingAgents;
    private final Config config;

    public CreateSessionStage(ProcessingStage<C> next,
                              TorrentRegistry torrentRegistry,
                              EventSource eventSource,
                              IConnectionSource connectionSource,
                              IMessageDispatcher messageDispatcher,
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
        IPeerWorkerFactory peerWorkerFactory = new PeerWorkerFactory(router);

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
