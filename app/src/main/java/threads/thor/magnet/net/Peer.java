package threads.thor.magnet.net;

import java.net.InetAddress;
import java.util.Optional;

import threads.thor.magnet.peer.PeerOptions;

public interface Peer {

    /**
     * @return Peer Internet address.
     * @since 1.0
     */
    InetAddress getInetAddress();

    /**
     * @return true, if the peer's listening port is not known yet
     * @see #getPort()
     * @since 1.9
     */
    boolean isPortUnknown();

    int getPort();

    /**
     * @return Optional peer ID
     * @since 1.0
     */
    Optional<PeerId> getPeerId();

    /**
     * @return Peer options and preferences
     * @since 1.2
     */
    PeerOptions getOptions();
}
