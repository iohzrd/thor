
package threads.thor.bt.torrent.data;

import java.nio.ByteBuffer;

public interface BlockReader {

    boolean readTo(ByteBuffer buffer);
}
