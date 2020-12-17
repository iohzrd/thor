package threads.thor.bt.processor;

import java.util.concurrent.CompletableFuture;

import threads.thor.bt.processor.listener.ListenerSource;

public interface Processor<C extends ProcessingContext> {

    CompletableFuture<?> process(C context, ListenerSource<C> listenerSource);
}
