/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
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

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.data.DataDescriptor;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentFile;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.TerminateOnErrorProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.messaging.Assignments;
import threads.thor.bt.torrent.selector.IncompletePiecesValidator;
import threads.thor.bt.torrent.selector.PieceSelector;
import threads.thor.bt.torrent.selector.ValidatingSelector;

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
        Torrent torrent = context.getTorrent().get();
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
