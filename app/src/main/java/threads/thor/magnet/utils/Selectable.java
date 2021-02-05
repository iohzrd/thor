package threads.thor.magnet.utils;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;


public interface Selectable {
    SelectableChannel getChannel();


    void selectionEvent(SelectionKey key) throws IOException;

    void doStateChecks() throws IOException;

    int calcInterestOps();
}
