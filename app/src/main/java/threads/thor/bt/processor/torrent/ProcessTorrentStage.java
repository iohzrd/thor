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

import threads.LogUtils;
import threads.thor.bt.event.EventSink;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.TerminateOnErrorProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.TrackerAnnouncer;
import threads.thor.bt.tracker.AnnounceKey;
import threads.thor.bt.tracker.ITrackerService;

public class ProcessTorrentStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final TorrentRegistry torrentRegistry;
    private final ITrackerService trackerService;
    private final EventSink eventSink;

    public ProcessTorrentStage(ProcessingStage<C> next,
                               TorrentRegistry torrentRegistry,
                               ITrackerService trackerService,
                               EventSink eventSink) {
        super(next);
        this.torrentRegistry = torrentRegistry;
        this.trackerService = trackerService;
        this.eventSink = eventSink;
    }

    @Override
    protected void doExecute(C context) {
        TorrentId torrentId = context.getTorrentId().get();
        TorrentDescriptor descriptor = getDescriptor(torrentId);

        Torrent torrent = context.getTorrent().get();
        AnnounceKey announceKey = torrent.getAnnounceKey();
        if (announceKey != null) {
            TrackerAnnouncer announcer = new TrackerAnnouncer(trackerService, torrent, announceKey, context.getState().get());
            context.setAnnouncer(announcer);
        }

        descriptor.start();
        start(context);

        eventSink.fireTorrentStarted(torrentId);

        while (descriptor.isActive()) {
            try {
                Thread.sleep(1000);
                if (context.getState().get().getPiecesRemaining() == 0) {
                    complete(context);
                    break;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted", e);
            }
        }
    }

    private void start(C context) {
        try {
            onStarted(context);
        } catch (Exception e) {
            LogUtils.error(LogUtils.TAG, e);
        }
    }

    protected void onStarted(C context) {
        context.getAnnouncer().ifPresent(TrackerAnnouncer::start);
    }

    private void complete(C context) {
        try {
            context.getTorrentId().ifPresent(torrentId -> getDescriptor(torrentId).complete());
            onCompleted(context);
        } catch (Exception e) {
            LogUtils.error(LogUtils.TAG, e);
        }
    }

    private void onCompleted(C context) {
        context.getAnnouncer().ifPresent(TrackerAnnouncer::complete);
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for threads.torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return ProcessingEvent.DOWNLOAD_COMPLETE;
    }
}
