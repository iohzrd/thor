package threads.thor.bt.processor;

public abstract class RoutingProcessingStage<C extends ProcessingContext> implements ProcessingStage<C> {

    private final ProcessingStage<C> next;

    RoutingProcessingStage(ProcessingStage<C> next) {
        this.next = next;
    }

    @Override
    public ProcessingStage<C> execute(C context) {
        return doExecute(context, next);
    }


    protected abstract ProcessingStage<C> doExecute(C context, ProcessingStage<C> next);
}
