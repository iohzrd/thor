package threads.thor.bt.torrent;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.net.ConnectionKey;

public class Assignments {


    private final Bitfield bitfield;
    private final PieceSelector selector;
    private final PieceStatistics pieceStatistics;

    private final Set<Integer> assignedPieces;
    private final Map<ConnectionKey, Assignment> assignments;

    public Assignments(Bitfield bitfield, PieceSelector selector, PieceStatistics pieceStatistics) {
        this.bitfield = bitfield;
        this.selector = selector;
        this.pieceStatistics = pieceStatistics;

        this.assignedPieces = new HashSet<>();
        this.assignments = new HashMap<>();
    }

    public Assignment get(ConnectionKey connectionKey) {
        return assignments.get(connectionKey);
    }

    public void remove(Assignment assignment) {
        assignment.abort();
        assignments.remove(assignment.getConnectionKey());
        // TODO: investigate on how this might affect endgame?
        assignedPieces.removeAll(assignment.getPieces());
    }

    public int count() {
        return assignments.size();
    }

    Optional<Assignment> assign(ConnectionKey connectionKey) {
        if (!hasInterestingPieces(connectionKey)) {
            return Optional.empty();
        }

        Assignment assignment = new Assignment(connectionKey,
                selector, pieceStatistics, this);
        assignments.put(connectionKey, assignment);
        return Optional.of(assignment);
    }

    boolean claim(int pieceIndex) {
        boolean claimed = !bitfield.isComplete(pieceIndex) && (isEndgame()
                || !assignedPieces.contains(pieceIndex));
        if (claimed) {
            assignedPieces.add(pieceIndex);
        }
        return claimed;
    }

    public void finish(Integer pieceIndex) {
        assignedPieces.remove(pieceIndex);
    }

    boolean isEndgame() {
        // if all remaining pieces are requested,
        // that would mean that we have entered the "endgame" mode
        return bitfield.getPiecesRemaining() <= assignedPieces.size();
    }

    /**
     * @return Collection of peers that have interesting pieces and can be given an assignment
     */
    public Set<ConnectionKey> update(Set<ConnectionKey> ready, Set<ConnectionKey> choking) {
        Set<ConnectionKey> result = new HashSet<>();
        for (ConnectionKey peer : ready) {
            if (hasInterestingPieces(peer)) {
                result.add(peer);
            }
        }
        for (ConnectionKey peer : choking) {
            if (hasInterestingPieces(peer)) {
                result.add(peer);
            }
        }

        return result;
    }

    private boolean hasInterestingPieces(ConnectionKey connectionKey) {
        Optional<Bitfield> peerBitfieldOptional = pieceStatistics.getPeerBitfield(connectionKey);
        if (!peerBitfieldOptional.isPresent()) {
            return false;
        }
        BitSet peerBitfield = peerBitfieldOptional.get().getBitmask();
        BitSet localBitfield = bitfield.getBitmask();
        peerBitfield.andNot(localBitfield);
        return peerBitfield.cardinality() > 0;
    }
}
