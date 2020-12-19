package threads.thor.bt.processor;

public interface ProcessingStage {


    ProcessingEvent after();


    ProcessingStage execute(MagnetContext context);
}
