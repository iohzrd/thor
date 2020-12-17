package threads.thor.bt.processor;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import threads.thor.bt.Config;
import threads.thor.bt.data.Bitfield;
import threads.thor.bt.data.DataDescriptor;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentFile;
import threads.thor.bt.torrent.Assignments;
import threads.thor.bt.torrent.IncompletePiecesValidator;
import threads.thor.bt.torrent.PieceSelector;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.ValidatingSelector;

public class ChooseFilesStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {
    private final TorrentRegistry torrentRegistry;
    private final Config config;

    public ChooseFilesStage(ProcessingStage<C> next,
                            TorrentRegistry torrentRegistry,
                            Config config) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.config = config;
    }

    @Override
    protected void doExecute(C context) {
        Torrent torrent = context.getTorrent();
        TorrentDescriptor descriptor = torrentRegistry.getDescriptor(torrent.getTorrentId()).get();

        Set<TorrentFile> selectedFiles = new HashSet<>(torrent.getFiles());


        Bitfield bitfield = descriptor.getDataDescriptor().getBitfield();
        Set<Integer> validPieces = getValidPieces(descriptor.getDataDescriptor(), selectedFiles);
        PieceSelector selector = createSelector(context.getPieceSelector(), bitfield, validPieces);
        PieceStatistics pieceStatistics = context.getPieceStatistics();
        Assignments assignments = new Assignments(bitfield, selector, pieceStatistics, config);

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
