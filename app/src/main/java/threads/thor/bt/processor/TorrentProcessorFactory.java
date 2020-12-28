package threads.thor.bt.processor;

import androidx.annotation.NonNull;

import threads.thor.bt.Runtime;

public class TorrentProcessorFactory {


    public static ChainProcessor createMagnetProcessor(@NonNull Runtime runtime) {


        ProcessingStage stage5 = new SeedStage(null,
                runtime.mTorrentRegistry);

        ProcessingStage stage4 = new ProcessMagnetTorrentStage(stage5,
                runtime.mTorrentRegistry, runtime.getEventBus());

        ProcessingStage stage3 = new ChooseFilesStage(stage4, runtime.mTorrentRegistry);

        ProcessingStage stage2 = new InitializeMagnetTorrentProcessingStage(stage3,
                runtime.mConnectionPool,
                runtime.mTorrentRegistry,
                runtime.mDataWorker,
                runtime.mBufferedPieceRegistry,
                runtime.getEventBus());


        ProcessingStage stage1 = new FetchMetadataStage(stage2,
                runtime.mTorrentRegistry,
                runtime.mPeerRegistry,
                runtime.getEventBus());


        ProcessingStage stage0 = new CreateSessionStage(stage1,
                runtime.mTorrentRegistry,
                runtime.getEventBus(),
                runtime.mConnectionSource,
                runtime.mMessageDispatcher,
                runtime.mMessagingAgents);

        return new ChainProcessor(stage0, runtime.getExecutor(),
                new TorrentContextFinalizer(runtime.mTorrentRegistry, runtime.getEventBus()));
    }


}
