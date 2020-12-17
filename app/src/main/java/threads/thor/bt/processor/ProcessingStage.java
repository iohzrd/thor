package threads.thor.bt.processor;

public interface ProcessingStage<C extends ProcessingContext> {


    ProcessingEvent after();


    ProcessingStage<C> execute(C context);
}
