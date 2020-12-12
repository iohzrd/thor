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

package threads.thor.bt.torrent;

import java.util.Optional;

import threads.LogUtils;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.tracker.AnnounceKey;
import threads.thor.bt.tracker.ITrackerService;
import threads.thor.bt.tracker.Tracker;
import threads.thor.bt.tracker.TrackerRequestBuilder;
import threads.thor.bt.tracker.TrackerResponse;

/**
 * @since 1.3
 */
public class TrackerAnnouncer {

    private final Optional<Tracker> trackerOptional;
    private final Torrent torrent;
    private final TorrentSessionState sessionState;

    public TrackerAnnouncer(ITrackerService trackerService,
                            Torrent torrent,
                            AnnounceKey announceKey,
                            TorrentSessionState sessionState) {
        this.trackerOptional = Optional.ofNullable(createTracker(trackerService, announceKey));
        this.torrent = torrent;
        this.sessionState = sessionState;
    }

    private Tracker createTracker(ITrackerService trackerService, AnnounceKey announceKey) {
        try {
            String trackerUrl = getTrackerUrl(announceKey);
            if (trackerService.isSupportedProtocol(trackerUrl)) {
                return trackerService.getTracker(announceKey);
            } else {
                LogUtils.error(LogUtils.TAG, "Tracker URL protocol is not supported: " + trackerUrl);
            }
        } catch (Exception e) {
            LogUtils.error(LogUtils.TAG, "Failed to create tracker for announce key: " + announceKey);
        }
        return null;
    }

    private String getTrackerUrl(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            return announceKey.getTrackerUrls().get(0).get(0);
        } else {
            return announceKey.getTrackerUrl();
        }
    }

    public void start() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.start, tracker, prepareAnnounce(tracker).start());
            } catch (Exception e) {
                logTrackerError(Event.start, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void stop() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.stop, tracker, prepareAnnounce(tracker).stop());
            } catch (Exception e) {
                logTrackerError(Event.stop, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    public void complete() {
        trackerOptional.ifPresent(tracker -> {
            try {
                processResponse(Event.complete, tracker, prepareAnnounce(tracker).complete());
            } catch (Exception e) {
                logTrackerError(Event.complete, tracker, Optional.of(e), Optional.empty());
            }
        });
    }

    private TrackerRequestBuilder prepareAnnounce(Tracker tracker) {
        return tracker.request(torrent.getTorrentId())
                .downloaded(sessionState.getDownloaded())
                .uploaded(sessionState.getUploaded())
                .left(sessionState.getPiecesRemaining() * torrent.getChunkSize());
    }

    private void processResponse(Event event, Tracker tracker, TrackerResponse response) {
        if (!response.isSuccess()) {
            logTrackerError(event, tracker, response.getError(), Optional.ofNullable(response.getErrorMessage()));
        }
    }

    private void logTrackerError(Event event, Tracker tracker, Optional<Throwable> e, Optional<String> message) {
        String log = String.format("Failed to announce '%s' event due to unexpected error " +
                "during interaction with the tracker: %s", event.name(), tracker);
        if (message.isPresent()) {
            log += "; message: " + message;
        }

    }

    private enum Event {
        start, stop, complete
    }
}
