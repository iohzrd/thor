package threads.thor.bt.kad;

import java.net.InetAddress;
import java.util.List;

interface DHTIndexingListener {

    List<PeerAddressDBItem> incomingPeersRequest(Key infoHash, InetAddress sourceAddress, Key nodeID);


}
