package threads.thor.magnet.processor;

public interface ProcessingStage {


    ProcessingEvent after();


    ProcessingStage execute(MagnetContext context);
}
