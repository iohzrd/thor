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

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.IPeerConnectionPool;
import threads.thor.bt.net.extended.ExtendedHandshakeConsumer;
import threads.thor.bt.net.pipeline.IBufferedPieceRegistry;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.TerminateOnErrorProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.data.DataWorker;
import threads.thor.bt.torrent.messaging.BitfieldConsumer;
import threads.thor.bt.torrent.messaging.GenericConsumer;
import threads.thor.bt.torrent.messaging.MetadataProducer;
import threads.thor.bt.torrent.messaging.PeerRequestConsumer;
import threads.thor.bt.torrent.messaging.PieceConsumer;
import threads.thor.bt.torrent.messaging.RequestProducer;

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
        Torrent torrent = context.getTorrent().get();
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
        context.getRouter().registerMessagingAgent(new MetadataProducer(() -> context.getTorrent().orElse(null), config));

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
