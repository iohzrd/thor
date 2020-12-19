package threads.thor.bt.utils;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import threads.thor.bt.net.InetPeer;
import threads.thor.bt.net.Peer;
import threads.thor.bt.peer.PeerOptions;
import threads.thor.bt.protocol.crypto.EncryptionPolicy;

public class CompactPeerInfo implements Iterable<Peer> {

    private static final int PORT_LENGTH = 2;
    private final int addressLength;
    private final byte[] peers;
    private final byte[] cryptoFlags;
    private final List<Peer> peerList;


    public CompactPeerInfo(byte[] peers, AddressType addressType) {
        this(peers, addressType, null);
    }


    public CompactPeerInfo(byte[] peers, AddressType addressType, byte[] cryptoFlags) {
        Objects.requireNonNull(peers);
        Objects.requireNonNull(addressType);

        int peerLength = addressType.length() + PORT_LENGTH;
        if (peers.length % peerLength != 0) {
            throw new IllegalArgumentException("Invalid peers string (" + addressType.name() + ") -- length (" +
                    peers.length + ") is not divisible by " + peerLength);
        }
        int numOfPeers = peers.length / peerLength;
        if (cryptoFlags != null && cryptoFlags.length != numOfPeers) {
            throw new IllegalArgumentException("Number of peers (" + numOfPeers +
                    ") is different from the number of crypto flags (" + cryptoFlags.length + ")");
        }
        this.addressLength = addressType.length();
        this.peers = peers;
        this.cryptoFlags = cryptoFlags;

        this.peerList = new ArrayList<>();
    }

    @NonNull
    @Override
    public Iterator<Peer> iterator() {
        if (!peerList.isEmpty()) {
            return peerList.iterator();
        }

        return new Iterator<Peer>() {
            private int pos, index;

            @Override
            public boolean hasNext() {
                return pos < peers.length;
            }

            @Override
            public Peer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more peers left");
                }

                int from, to;
                InetAddress inetAddress;
                int port;

                from = pos;
                to = pos = pos + addressLength;
                try {
                    inetAddress = InetAddress.getByAddress(Arrays.copyOfRange(peers, from, to));
                } catch (UnknownHostException e) {
                    throw new RuntimeException("Failed to get next peer", e);
                }

                from = to;
                to = pos = pos + PORT_LENGTH;
                port = (((peers[from] << 8) & 0xFF00) + (peers[to - 1] & 0x00FF));

                PeerOptions options = PeerOptions.defaultOptions();
                boolean requiresEncryption = cryptoFlags != null && cryptoFlags[index] == 1;
                if (requiresEncryption) {
                    options = options.withEncryptionPolicy(EncryptionPolicy.PREFER_ENCRYPTED);
                }
                Peer peer = InetPeer.builder(inetAddress, port).options(options).build();
                peerList.add(peer);
                index++;

                return peer;
            }
        };
    }

    /**
     * Address family
     *
     * @since 1.0
     */
    public enum AddressType {

        /**
         * Internet Protocol v4
         *
         * @since 1.0
         */
        IPV4(4),

        /**
         * Internet Protocol v6
         *
         * @since 1.0
         */
        IPV6(16);

        private final int length;

        AddressType(int length) {
            this.length = length;
        }

        /**
         * @return Address length in bytes
         * @since 1.0
         */
        public int length() {
            return length;
        }
    }
}
