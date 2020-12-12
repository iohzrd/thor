package threads.thor.bt.utils;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;


public interface Selectable {
    SelectableChannel getChannel();

    void registrationEvent(NIOConnectionManager manager, SelectionKey key) throws IOException;

    void selectionEvent(SelectionKey key) throws IOException;

    void doStateChecks(long now) throws IOException;

    int calcInterestOps();
}
