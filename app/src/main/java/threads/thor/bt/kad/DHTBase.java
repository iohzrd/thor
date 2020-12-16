package threads.thor.bt.kad;

import threads.thor.bt.kad.messages.AbstractLookupRequest;
import threads.thor.bt.kad.messages.AnnounceRequest;
import threads.thor.bt.kad.messages.ErrorMessage;
import threads.thor.bt.kad.messages.GetPeersRequest;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.PingRequest;
import threads.thor.bt.kad.tasks.AnnounceTask;
import threads.thor.bt.kad.tasks.PeerLookupTask;
import threads.thor.bt.kad.tasks.TaskManager;
import threads.thor.bt.net.PeerId;

interface DHTBase {

    void start(PeerId peerId, int port);

    void stop();


    /**
     * Do an announce/scrape lookup on the DHT network
     *
     * @param info_hash The info_hash
     * @return The task which handles this
     */
    PeerLookupTask createPeerLookup(byte[] info_hash);

    /**
     * Perform the put() operation for an announce
     */
    AnnounceTask announce(PeerLookupTask lookup, boolean isSeed, int btPort);

    /**
     * Add a DHT node. This node shall be pinged immediately.
     *
     * @param host  The hostname or ip
     * @param hport The port of the host
     */
    void addDHTNode(String host, int hport);

    void started(int port);

    void ping(PingRequest r);

    void findNode(AbstractLookupRequest r);

    void response(MessageBase r);

    void getPeers(GetPeersRequest r);

    void announce(AnnounceRequest r);

    void error(ErrorMessage r);

    void timeout(RPCCall r);

    Node getNode();

    TaskManager getTaskManager();

}
