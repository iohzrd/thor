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

import threads.thor.bt.event.EventSink;
import threads.thor.bt.processor.ContextFinalizer;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.TrackerAnnouncer;

public class TorrentContextFinalizer<C extends TorrentContext> implements ContextFinalizer<C> {

    private final TorrentRegistry torrentRegistry;
    private final EventSink eventSink;

    public TorrentContextFinalizer(TorrentRegistry torrentRegistry, EventSink eventSink) {
        this.torrentRegistry = torrentRegistry;
        this.eventSink = eventSink;
    }

    @Override
    public void finalizeContext(C context) {
        torrentRegistry.getDescriptor(context.getTorrentId()).ifPresent(TorrentDescriptor::stop);
        eventSink.fireTorrentStopped(context.getTorrentId());
        context.getAnnouncer().ifPresent(TrackerAnnouncer::stop);
    }
}
