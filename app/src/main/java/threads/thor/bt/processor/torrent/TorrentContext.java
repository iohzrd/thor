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

import java.util.Optional;
import java.util.function.Supplier;

import threads.thor.bt.data.Bitfield;
import threads.thor.bt.data.Storage;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.processor.ProcessingContext;
import threads.thor.bt.torrent.PieceStatistics;
import threads.thor.bt.torrent.TorrentSessionState;
import threads.thor.bt.torrent.TrackerAnnouncer;
import threads.thor.bt.torrent.messaging.Assignments;
import threads.thor.bt.torrent.messaging.MessageRouter;
import threads.thor.bt.torrent.selector.PieceSelector;

/**
 * Accumulates data, that is specific to standard threads.torrent download/upload.
 *
 * @since 1.3
 */
public class TorrentContext implements ProcessingContext {

    private final PieceSelector pieceSelector;
    private final Storage storage;
    private final Supplier<Torrent> torrentSupplier;

    /* all of these can be missing, depending on which stage is currently being executed */
    private volatile TorrentId torrentId;
    private volatile Torrent torrent;
    private volatile TorrentSessionState state;
    private volatile MessageRouter router;
    private volatile Bitfield bitfield;
    private volatile Assignments assignments;
    private volatile PieceStatistics pieceStatistics;
    private volatile TrackerAnnouncer announcer;

    public TorrentContext(PieceSelector pieceSelector,
                          Storage storage,
                          Supplier<Torrent> torrentSupplier) {
        this.pieceSelector = pieceSelector;
        this.storage = storage;
        this.torrentSupplier = torrentSupplier;
    }


    public PieceSelector getPieceSelector() {
        return pieceSelector;
    }


    public Storage getStorage() {
        return storage;
    }

    public Supplier<Torrent> getTorrentSupplier() {
        return torrentSupplier;
    }

    @Override
    public TorrentId getTorrentId() {
        return torrentId;
    }

    public void setTorrentId(TorrentId torrentId) {
        this.torrentId = torrentId;
    }

    @Override
    public Optional<Torrent> getTorrent() {
        return Optional.ofNullable(torrent);
    }

    public void setTorrent(Torrent torrent) {
        this.torrent = torrent;
    }

    @Override
    public Optional<TorrentSessionState> getState() {
        return Optional.ofNullable(state);
    }

    public void setState(TorrentSessionState state) {
        this.state = state;
    }

    public MessageRouter getRouter() {
        return router;
    }

    public void setRouter(MessageRouter router) {
        this.router = router;
    }

    public Bitfield getBitfield() {
        return bitfield;
    }

    public void setBitfield(Bitfield bitfield) {
        this.bitfield = bitfield;
    }

    public Assignments getAssignments() {
        return assignments;
    }

    public void setAssignments(Assignments assignments) {
        this.assignments = assignments;
    }

    public PieceStatistics getPieceStatistics() {
        return pieceStatistics;
    }

    public void setPieceStatistics(PieceStatistics pieceStatistics) {
        this.pieceStatistics = pieceStatistics;
    }

    public Optional<TrackerAnnouncer> getAnnouncer() {
        return Optional.ofNullable(announcer);
    }

    public void setAnnouncer(TrackerAnnouncer announcer) {
        this.announcer = announcer;
    }
}
