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

package threads.thor.bt.processor.magnet;

import threads.thor.bt.event.EventSink;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.torrent.ProcessTorrentStage;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.tracker.ITrackerService;

public class ProcessMagnetTorrentStage extends ProcessTorrentStage<MagnetContext> {

    public ProcessMagnetTorrentStage(ProcessingStage<MagnetContext> next,
                                     TorrentRegistry torrentRegistry,
                                     ITrackerService trackerService,
                                     EventSink eventSink) {
        super(next, torrentRegistry, trackerService, eventSink);
    }

    @Override
    protected void onStarted(MagnetContext context) {
        // do not announce start, as it should have been done already per FetchMetadataStage
    }
}
