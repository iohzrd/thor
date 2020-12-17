package threads.thor.bt.processor;

import java.util.concurrent.CompletableFuture;

public interface Processor<C extends ProcessingContext> {

    CompletableFuture<?> process(C context, ListenerSource<C> listenerSource);
}
