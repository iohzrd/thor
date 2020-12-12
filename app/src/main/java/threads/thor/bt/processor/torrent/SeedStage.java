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

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.TerminateOnErrorProcessingStage;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

public class SeedStage<C extends TorrentContext> extends TerminateOnErrorProcessingStage<C> {

    private final TorrentRegistry torrentRegistry;

    public SeedStage(ProcessingStage<C> next,
                     TorrentRegistry torrentRegistry) {
        super(next);
        this.torrentRegistry = torrentRegistry;
    }

    @Override
    protected void doExecute(C context) {
        TorrentDescriptor descriptor = getDescriptor(context.getTorrentId().get());

        while (descriptor.isActive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted", e);
            }
        }
    }

    private TorrentDescriptor getDescriptor(TorrentId torrentId) {
        return torrentRegistry.getDescriptor(torrentId)
                .orElseThrow(() -> new IllegalStateException("No descriptor present for threads.torrent ID: " + torrentId));
    }

    @Override
    public ProcessingEvent after() {
        return null;
    }
}
