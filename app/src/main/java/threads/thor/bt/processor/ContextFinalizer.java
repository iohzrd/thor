package threads.thor.bt.processor;

public interface ContextFinalizer<C extends ProcessingContext> {

    void finalizeContext(C context);
}
