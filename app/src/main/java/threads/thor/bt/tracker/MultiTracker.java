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

package threads.thor.bt.tracker;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import threads.thor.bt.BtException;
import threads.thor.bt.metainfo.TorrentId;

class MultiTracker implements Tracker {

    private final ITrackerService trackerService;
    private final List<List<Tracker>> trackerTiers;

    MultiTracker(ITrackerService trackerService, AnnounceKey announceKey) {
        this(trackerService, announceKey, true);
    }

    private MultiTracker(ITrackerService trackerService, AnnounceKey announceKey, boolean shouldShuffleTiers) {
        if (!announceKey.isMultiKey()) {
            throw new IllegalArgumentException("Not a multi key: " + announceKey);
        }
        this.trackerService = trackerService;
        this.trackerTiers = initTrackers(announceKey, shouldShuffleTiers);
    }

    private List<List<Tracker>> initTrackers(AnnounceKey announceKey, boolean shouldShuffleTiers) {

        List<List<String>> trackerUrls = announceKey.getTrackerUrls();
        List<List<Tracker>> trackers = new ArrayList<>(trackerUrls.size() + 1);

        for (List<String> tier : trackerUrls) {
            List<Tracker> tierTrackers = new ArrayList<>(tier.size() + 1);
            for (String trackerUrl : tier) {
                tierTrackers.add(new LazyTracker(() -> trackerService.getTracker(trackerUrl)));
            }
            // per BEP-12 spec each tier must be shuffled
            if (shouldShuffleTiers) {
                Collections.shuffle(tierTrackers);
            }
            trackers.add(tierTrackers);
        }

        return trackers;
    }

    @Override
    public TrackerRequestBuilder request(TorrentId torrentId) {
        return new TrackerRequestBuilder(torrentId) {

            @Override
            public TrackerResponse start() {
                return tryForAllTrackers(tracker -> getDelegate(tracker, torrentId).start());
            }

            @Override
            public TrackerResponse stop() {
                return tryForAllTrackers(tracker -> getDelegate(tracker, torrentId).stop());
            }

            @Override
            public TrackerResponse complete() {
                return tryForAllTrackers(tracker -> getDelegate(tracker, torrentId).complete());
            }

            @Override
            public TrackerResponse query() {
                return tryForAllTrackers(tracker -> getDelegate(tracker, torrentId).query());
            }

            private TrackerRequestBuilder getDelegate(Tracker tracker, TorrentId torrentId) {
                TrackerRequestBuilder delegate = tracker.request(torrentId);

                long downloaded = getDownloaded();
                if (downloaded > 0) {
                    delegate.downloaded(downloaded);
                }

                long uploaded = getUploaded();
                if (uploaded > 0) {
                    delegate.uploaded(uploaded);
                }

                long left = getLeft();
                if (left > 0) {
                    delegate.left(left);
                }

                return delegate;
            }

            private TrackerResponse tryForAllTrackers(Function<Tracker, TrackerResponse> func) {

                List<TrackerResponse> responses = new ArrayList<>();

                for (List<Tracker> trackerTier : trackerTiers) {

                    TrackerResponse response;
                    Tracker currentTracker;

                    for (int i = 0; i < trackerTier.size(); i++) {
                        currentTracker = trackerTier.get(i);
                        response = func.apply(currentTracker);
                        responses.add(response);

                        if (response.isSuccess()) {
                            if (trackerTier.size() > 1
                                    && i != 0) {
                                trackerTier.remove(i);
                                trackerTier.add(0, currentTracker);
                            }
                            return response;
                        } else if (response.getError().isPresent()) {
                            Throwable e = response.getError().get();

                        }
                    }
                }

                throw new BtException("All trackers failed; responses (in chrono order): " + responses);
            }
        };
    }

    @NonNull
    @Override
    public String toString() {
        return "MultiTracker{" +
                "trackerTiers=" + trackerTiers +
                '}';
    }

    private static class LazyTracker implements Tracker {

        private final Object lock;
        private final Supplier<Tracker> delegateSupplier;
        private volatile Tracker delegate;

        LazyTracker(Supplier<Tracker> delegateSupplier) {
            this.delegateSupplier = delegateSupplier;
            lock = new Object();
        }

        @Override
        public TrackerRequestBuilder request(TorrentId torrentId) {
            return getDelegate().request(torrentId);
        }

        private Tracker getDelegate() {

            if (delegate == null) {
                synchronized (lock) {
                    if (delegate == null) {
                        delegate = delegateSupplier.get();
                    }
                }
            }
            return delegate;
        }

        @NonNull
        @Override
        public String toString() {
            return getDelegate().toString();
        }
    }
}
