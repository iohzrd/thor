package threads.thor.bt.processor;


import threads.LogUtils;

public abstract class TerminateOnErrorProcessingStage extends RoutingProcessingStage {

    protected TerminateOnErrorProcessingStage(ProcessingStage next) {
        super(next);
    }

    @Override
    protected final ProcessingStage doExecute(MagnetContext context, ProcessingStage next) {
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
    protected abstract void doExecute(MagnetContext context);
}
