package threads.thor.bt.processor;

import java.util.concurrent.CompletableFuture;

import threads.thor.bt.processor.listener.ListenerSource;

/**
 * Generic asynchronous context processor.
 * <p>
 * Typically, a processor encapsulates a series of stages (a processing chain),
 * that should be performed in order to achieve some desired effect or produce some result.
 * <p>
 * It uses a context to transfer the processing state between different stages
 * and listeners to produce notifications about reaching significant processing milestones
 * and optionally perform context-based routing.
 *
 * @param <C> Type of processing context
 * @since 1.5
 */
public interface Processor<C extends ProcessingContext> {

    /**
     * Begin asynchronous processing of a given context.
     * <p>
     * Returns a future, that can be used for
     * - waiting synchronously for the processing to complete,
     * - building processing pipelines and coordinating parallel executions,
     * - completing or cancelling the processing.
     *
     * @param context        Processing context, that contains all necessary data to begin the processing
     * @param listenerSource Listeners
     * @return Future
     * @since 1.5
     */
    CompletableFuture<?> process(C context, ListenerSource<C> listenerSource);
}
