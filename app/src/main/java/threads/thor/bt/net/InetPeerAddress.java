package threads.thor.bt.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class InetPeerAddress {

    private final String hostname;
    private final int port;
    private final int hashCode;
    private final Object lock;
    private volatile InetAddress address;

    public InetPeerAddress(String hostname, int port) {
        this.hostname = Objects.requireNonNull(hostname);
        this.port = port;
        this.hashCode = 31 * hostname.hashCode() + port;
        this.lock = new Object();
    }


    public InetAddress getAddress() {
        if (address == null) {
            synchronized (lock) {
                if (address == null) {
                    try {
                        address = InetAddress.getByName(hostname);
                    } catch (UnknownHostException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return address;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || !getClass().equals(object.getClass())) {
            return false;
        }

        InetPeerAddress that = (InetPeerAddress) object;
        return port == that.port && hostname.equals(that.hostname);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
