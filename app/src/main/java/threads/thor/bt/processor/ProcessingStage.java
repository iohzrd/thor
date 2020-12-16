package threads.thor.bt.processor;

import threads.thor.bt.processor.listener.ProcessingEvent;

public interface ProcessingStage<C extends ProcessingContext> {


    ProcessingEvent after();


    ProcessingStage<C> execute(C context);
}
