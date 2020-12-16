package threads.thor.bt.processor;


import threads.LogUtils;

public abstract class TerminateOnErrorProcessingStage<C extends ProcessingContext> extends RoutingProcessingStage<C> {

    protected TerminateOnErrorProcessingStage(ProcessingStage<C> next) {
        super(next);
    }

    @Override
    protected final ProcessingStage<C> doExecute(C context, ProcessingStage<C> next) {
        try {
            doExecute(context);
        } catch (Exception e) {
            LogUtils.error(LogUtils.TAG, e);
            next = null; // terminate processing chain
        }
        return next;
    }

    /**
     * Perform processing. Implementations are free to throw exceptions,
     * in which case the processing chain will be terminated.
     */
    protected abstract void doExecute(C context);
}
