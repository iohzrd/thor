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

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
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
    private final Optional<ContextFinalizer<C>> finalizer;

    /**
     * Create processor for a given processing chain.
     *
     * @param chainHead First stage
     * @param executor  Asynchronous facility to use for executing the processing chain
     * @since 1.5
     */
    public ChainProcessor(ProcessingStage<C> chainHead,
                          ExecutorService executor) {
        this(chainHead, executor, Optional.empty());
    }

    /**
     * Create processor for a given processing chain.
     *
     * @param chainHead First stage
     * @param executor  Asynchronous facility to use for executing the processing chain
     * @param finalizer Context finalizer, that will be called,
     *                  when threads.torrent processing completes normally or terminates abruptly due to error
     * @since 1.5
     */
    public ChainProcessor(ProcessingStage<C> chainHead,
                          ExecutorService executor,
                          ContextFinalizer<C> finalizer) {
        this(chainHead, executor, Optional.of(finalizer));
    }

    private ChainProcessor(ProcessingStage<C> chainHead,
                           ExecutorService executor,
                           Optional<ContextFinalizer<C>> finalizer) {
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

            finalizer.ifPresent(f -> f.finalizeContext(context));
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
            finalizer.ifPresent(f -> f.finalizeContext(context));
        }
        return next;
    }
}
