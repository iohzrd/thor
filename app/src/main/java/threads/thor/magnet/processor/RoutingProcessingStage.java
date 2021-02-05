package threads.thor.magnet.processor;

public abstract class RoutingProcessingStage implements ProcessingStage {

    private final ProcessingStage next;

    RoutingProcessingStage(ProcessingStage next) {
        this.next = next;
    }

    @Override
    public ProcessingStage execute(MagnetContext context) {
        return doExecute(context, next);
    }


    protected abstract ProcessingStage doExecute(MagnetContext context, ProcessingStage next);
}
