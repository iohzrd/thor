package threads.thor.bt.processor;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

import threads.LogUtils;

public class ChainProcessor {

    private final ProcessingStage chainHead;
    private final ExecutorService executor;
    private final ContextFinalizer finalizer;


    public ChainProcessor(ProcessingStage chainHead, ExecutorService executor,
                          ContextFinalizer finalizer) {
        this.chainHead = chainHead;
        this.finalizer = finalizer;
        this.executor = executor;
    }

    public CompletableFuture<?> process(MagnetContext context, ListenerSource listenerSource) {
        Runnable r = () -> executeStage(chainHead, context, listenerSource);
        return CompletableFuture.runAsync(r, executor);
    }

    private void executeStage(ProcessingStage chainHead,
                              MagnetContext context,
                              ListenerSource listenerSource) {
        ProcessingEvent stageFinished = chainHead.after();
        Collection<BiFunction<MagnetContext, ProcessingStage, ProcessingStage>> listeners;
        if (stageFinished != null) {
            listeners = listenerSource.getListeners(stageFinished);
        } else {
            listeners = Collections.emptyList();
        }

        ProcessingStage next = doExecute(chainHead, context, listeners);
        if (next != null) {
            executeStage(next, context, listenerSource);
        }
    }

    private ProcessingStage doExecute(ProcessingStage stage,
                                      MagnetContext context,
                                      Collection<BiFunction<MagnetContext, ProcessingStage, ProcessingStage>> listeners) {


        ProcessingStage next;
        try {
            next = stage.execute(context);

        } catch (Exception e) {
            if (finalizer != null) {
                finalizer.finalizeContext(context);
            }
            throw e;
        }

        for (BiFunction<MagnetContext, ProcessingStage, ProcessingStage> listener : listeners) {
            try {
                // TODO: different listeners may return different next stages (including nulls)
                next = listener.apply(context, next);
            } catch (Exception e) {
                LogUtils.error(LogUtils.TAG, "Listener invocation failed", e);
            }
        }

        if (next == null) {
            if (finalizer != null) {
                finalizer.finalizeContext(context);
            }
        }
        return next;
    }
}
