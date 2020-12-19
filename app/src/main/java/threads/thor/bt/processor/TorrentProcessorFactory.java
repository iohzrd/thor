package threads.thor.bt.processor;

import androidx.annotation.NonNull;

import threads.thor.bt.Runtime;

public class TorrentProcessorFactory {


    public static ChainProcessor<MagnetContext> createMagnetProcessor(@NonNull Runtime runtime) {


        ProcessingStage<MagnetContext> stage5 = new SeedStage<>(null,
                runtime.mTorrentRegistry);

        ProcessingStage<MagnetContext> stage4 = new ProcessMagnetTorrentStage(stage5,
                runtime.mTorrentRegistry, runtime.getEventBus());

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
