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

package threads.thor.bt.processor.magnet;

import java.util.Collection;
import java.util.HashSet;

import threads.LogUtils;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.net.IPeerConnectionPool;
import threads.thor.bt.net.pipeline.IBufferedPieceRegistry;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.processor.torrent.InitializeTorrentProcessingStage;
import threads.thor.bt.protocol.BitOrder;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.data.DataWorker;

public class InitializeMagnetTorrentProcessingStage extends InitializeTorrentProcessingStage<MagnetContext> {

    private final EventSink eventSink;

    public InitializeMagnetTorrentProcessingStage(ProcessingStage<MagnetContext> next,
                                                  IPeerConnectionPool connectionPool,
                                                  TorrentRegistry torrentRegistry,
                                                  DataWorker dataWorker,
                                                  IBufferedPieceRegistry bufferedPieceRegistry,
                                                  EventSink eventSink,
                                                  Config config) {
        super(next, connectionPool, torrentRegistry, dataWorker, bufferedPieceRegistry, eventSink, config);
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        super.doExecute(context);

        TorrentId torrentId = context.getTorrentId().get();

        PieceStatistics statistics = context.getPieceStatistics();
        // process bitfields and haves that we received while fetching metadata
        Collection<ConnectionKey> peersUpdated = new HashSet<>();
        context.getBitfieldConsumer().getBitfields().forEach((peer, bitfieldBytes) -> {
            if (statistics.getPeerBitfield(peer).isPresent()) {
                // we should not have received peer's bitfields twice, but whatever.. ignore and continue
                return;
            }
            try {
                peersUpdated.add(peer);
                statistics.addBitfield(peer, new Bitfield(bitfieldBytes, BitOrder.LITTLE_ENDIAN, statistics.getPiecesTotal()));
            } catch (Exception e) {
                LogUtils.error(LogUtils.TAG, "Error happened when processing peer's bitfield", e);
            }
        });
        context.getBitfieldConsumer().getHaves().forEach((peer, pieces) -> {
            try {
                peersUpdated.add(peer);
                pieces.forEach(piece -> statistics.addPiece(peer, piece));
            } catch (Exception e) {
                LogUtils.error(LogUtils.TAG, "Error happened when processing peer's haves", e);
            }
        });
        peersUpdated.forEach(peer -> {
            // racing against possible disconnection of peers, so must check if bitfield is still present
            statistics.getPeerBitfield(peer).ifPresent(
                    bitfield -> eventSink.firePeerBitfieldUpdated(torrentId, peer, bitfield));
        });
        // unregistering only now, so that there were no gaps in bitfield receiving
        context.getRouter().unregisterMessagingAgent(context.getBitfieldConsumer());
        context.setBitfieldConsumer(null); // mark for gc collection
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
