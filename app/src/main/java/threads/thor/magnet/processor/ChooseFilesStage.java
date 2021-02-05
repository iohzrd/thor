package threads.thor.magnet.processor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import threads.thor.magnet.data.Bitfield;
import threads.thor.magnet.data.DataDescriptor;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentFile;
import threads.thor.magnet.torrent.Assignments;
import threads.thor.magnet.torrent.IncompletePiecesValidator;
import threads.thor.magnet.torrent.PieceSelector;
import threads.thor.magnet.torrent.PieceStatistics;
import threads.thor.magnet.torrent.TorrentDescriptor;
import threads.thor.magnet.torrent.TorrentRegistry;
import threads.thor.magnet.torrent.ValidatingSelector;

public class ChooseFilesStage extends TerminateOnErrorProcessingStage {
    private final TorrentRegistry torrentRegistry;


    public ChooseFilesStage(ProcessingStage next, TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(MagnetContext context) {
        Torrent torrent = context.getTorrent();
        Objects.requireNonNull(torrent);

        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrent.getTorrentId()).get();

        Set<TorrentFile> selectedFiles = new HashSet<>(torrent.getFiles());


        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        Set<Integer> validPieces = getValidPieces(descriptor.getDataDescriptor(), selectedFiles);
        PieceSelector selector = createSelector(context.getPieceSelector(), bitfield, validPieces);
        PieceStatistics pieceStatistics = context.getPieceStatistics();
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics);

        updateSkippedPieces(bitfield, validPieces);
        context.setAssignments(assignments);
    }

    private void updateSkippedPieces(Bitfield bitfield, Set<Integer> validPieces) {
        IntStream.range(0, bitfield.getPiecesTotal()).forEach(pieceIndex -> {
            if (!validPieces.contains(pieceIndex)) {
                bitfield.skip(pieceIndex);
            }
        });
    }

    private Set<Integer> getValidPieces(DataDescriptor dataDescriptor, Set<TorrentFile> selectedFiles) {
        Set<Integer> validPieces = new HashSet<>();
        IntStream.range(0, dataDescriptor.getBitfield().getPiecesTotal()).forEach(pieceIndex -> {
            for (TorrentFile file : dataDescriptor.getFilesForPiece(pieceIndex)) {
                if (selectedFiles.contains(file)) {
                    validPieces.add(pieceIndex);
                    break;
                }
            }
        });
        return validPieces;
    }

    private PieceSelector createSelector(PieceSelector selector,
                                         Bitfield bitfield,
                                         Set<Integer> selectedFilesPieces) {
        IntPredicate incompletePiecesValidator = new IncompletePiecesValidator(bitfield);
        IntPredicate selectedFilesValidator = selectedFilesPieces::contains;
        IntPredicate validator = (pieceIndex) ->
                selectedFilesValidator.test(pieceIndex) && incompletePiecesValidator.test(pieceIndex);
        return new ValidatingSelector(validator, selector);
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.FILES_CHOSEN;
    }
}
