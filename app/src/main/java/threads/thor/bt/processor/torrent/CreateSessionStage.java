/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.processor.torrent;

import java.util.Set;
import java.util.function.Supplier;

import threads.thor.bt.IAgent;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.IConnectionSource;
import threads.thor.bt.net.IMessageDispatcher;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.TerminateOnErrorProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.runtime.Config;
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
        TorrentWorker torrentWorker = new TorrentWorker(torrentId, messageDispatcher,
                connectionSource, peerWorkerFactory,
                bitfieldSupplier, assignmentsSupplier, statisticsSupplier, eventSource, config);

        context.setState(new TorrentSessionState(descriptor, torrentWorker));
        context.setRouter(router);
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
