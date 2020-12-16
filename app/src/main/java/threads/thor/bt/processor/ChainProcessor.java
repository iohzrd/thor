package threads.thor.bt.processor;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

import threads.LogUtils;
import threads.thor.bt.processor.listener.ListenerSource;
import threads.thor.bt.processor.listener.ProcessingEvent;

/**
 * Base implementation of a generic asynchronous executor of processing chains.
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public class ChainProcessor<C extends ProcessingContext> implements Processor<C> {

    private final ProcessingStage<C> chainHead;
    private final ExecutorService executor;
    private final ContextFinalizer<C> finalizer;


    public ChainProcessor(ProcessingStage<C> chainHead, ExecutorService executor,
                          ContextFinalizer<C> finalizer) {
        this.chainHead = chainHead;
        this.finalizer = finalizer;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<?> process(C context, ListenerSource<C> listenerSource) {
        Runnable r = () -> executeStage(chainHead, context, listenerSource);
        return CompletableFuture.runAsync(r, executor);
    }

    private void executeStage(ProcessingStage<C> chainHead,
                              C context,
                              ListenerSource<C> listenerSource) {
        ProcessingEvent stageFinished = chainHead.after();
        Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> listeners;
        if (stageFinished != null) {
            listeners = listenerSource.getListeners(stageFinished);
        } else {
            listeners = Collections.emptyList();
        }

        ProcessingStage<C> next = doExecute(chainHead, context, listeners);
        if (next != null) {
            executeStage(next, context, listenerSource);
        }
    }

    private ProcessingStage<C> doExecute(ProcessingStage<C> stage,
                                         C context,
                                         Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> listeners) {


        ProcessingStage<C> next;
        try {
            next = stage.execute(context);

        } catch (Exception e) {
            if (finalizer != null) {
                finalizer.finalizeContext(context);
            }
            throw e;
        }

        for (BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener : listeners) {
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
