package threads.thor.magnet.processor;

import java.util.Collection;
import java.util.HashSet;

import threads.LogUtils;
import threads.thor.magnet.data.Bitfield;
import threads.thor.magnet.event.EventSink;
import threads.thor.magnet.net.ConnectionKey;
import threads.thor.magnet.net.PeerConnectionPool;
import threads.thor.magnet.net.pipeline.BufferedPieceRegistry;
import threads.thor.magnet.protocol.BitOrder;
import threads.thor.magnet.torrent.DataWorker;
import threads.thor.magnet.torrent.PieceStatistics;
import threads.thor.magnet.torrent.TorrentRegistry;

public class InitializeMagnetTorrentProcessingStage extends InitializeTorrentProcessingStage {

    private final EventSink eventSink;

    public InitializeMagnetTorrentProcessingStage(ProcessingStage next,
                                                  PeerConnectionPool connectionPool,
                                                  TorrentRegistry torrentRegistry,
                                                  DataWorker dataWorker,
                                                  BufferedPieceRegistry bufferedPieceRegistry,
                                                  EventSink eventSink) {
        super(next, connectionPool, torrentRegistry, dataWorker, bufferedPieceRegistry, eventSink);
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        super.doExecute(context);

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
                    bitfield -> eventSink.firePeerBitfieldUpdated(peer));
        });
        // unregistering only now, so that there were no gaps in bitfield receiving

        context.setBitfieldConsumer(null); // mark for gc collection
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
