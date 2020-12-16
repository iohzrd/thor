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

package threads.thor.bt.processor;

import androidx.annotation.NonNull;

import threads.thor.bt.processor.magnet.FetchMetadataStage;
import threads.thor.bt.processor.magnet.InitializeMagnetTorrentProcessingStage;
import threads.thor.bt.processor.magnet.MagnetContext;
import threads.thor.bt.processor.magnet.ProcessMagnetTorrentStage;
import threads.thor.bt.processor.torrent.ChooseFilesStage;
import threads.thor.bt.processor.torrent.CreateSessionStage;
import threads.thor.bt.processor.torrent.SeedStage;
import threads.thor.bt.processor.torrent.TorrentContextFinalizer;
import threads.thor.bt.runtime.BtRuntime;

public class TorrentProcessorFactory {


    public static ChainProcessor<MagnetContext> createMagnetProcessor(@NonNull BtRuntime runtime) {


        ProcessingStage<MagnetContext> stage5 = new SeedStage<>(null,
                runtime.mTorrentRegistry);

        ProcessingStage<MagnetContext> stage4 = new ProcessMagnetTorrentStage(stage5,
                runtime.mTorrentRegistry, runtime.mTrackerService, runtime.getEventBus());

        ProcessingStage<MagnetContext> stage3 = new ChooseFilesStage<>(stage4,
                runtime.mTorrentRegistry, runtime.getConfig());

        ProcessingStage<MagnetContext> stage2 = new InitializeMagnetTorrentProcessingStage(stage3,
                runtime.mConnectionPool,
                runtime.mTorrentRegistry,
                runtime.mDataWorker,
                runtime.mBufferedPieceRegistry,
                runtime.getEventBus(),
                runtime.getConfig());


        ProcessingStage<MagnetContext> stage1 = new FetchMetadataStage(stage2,
                runtime.mTorrentRegistry,
                runtime.mPeerRegistry,
                runtime.getEventBus(),
                runtime.getConfig(),
                runtime.getContext());


        ProcessingStage<MagnetContext> stage0 = new CreateSessionStage<>(stage1,
                runtime.mTorrentRegistry,
                runtime.getEventBus(),
                runtime.mConnectionSource,
                runtime.mMessageDispatcher,
                runtime.mMessagingAgents,
                runtime.getConfig());

        return new ChainProcessor<>(stage0, runtime.getExecutor(),
                new TorrentContextFinalizer<>(runtime.mTorrentRegistry, runtime.getEventBus()));
    }


}
