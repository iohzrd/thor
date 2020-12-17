
package threads.thor.bt.metainfo;

import java.util.Optional;

public interface TorrentSource {

    Optional<byte[]> getMetadata();

    byte[] getExchangedMetadata();

}
