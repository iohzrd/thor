package threads.thor.magnet.magnet;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import threads.thor.magnet.metainfo.TorrentId;
import threads.thor.magnet.net.InetPeerAddress;

public class MagnetUri {

    private final TorrentId torrentId;
    private final String displayName;
    private final Collection<String> trackerUrls;
    private final Collection<InetPeerAddress> peerAddresses;

    private MagnetUri(TorrentId torrentId,
                      String displayName,
                      Collection<String> trackerUrls,
                      Collection<InetPeerAddress> peerAddresses) {
        this.torrentId = torrentId;
        this.displayName = displayName;
        this.trackerUrls = (trackerUrls == null) ? Collections.emptyList() : trackerUrls;
        this.peerAddresses = (peerAddresses == null) ? Collections.emptyList() : peerAddresses;
    }

    /**
     * Start building a magnet link for a given threads.torrent.
     *
     * @since 1.3
     */
    public static Builder torrentId(TorrentId torrentId) {
        return new Builder(torrentId);
    }

    @NonNull
    @Override
    public String toString() {
        return "MagnetUri{" +
                "torrentId=" + torrentId +
                ", displayName=" + displayName +
                ", trackerUrls=" + trackerUrls +
                ", peerAddresses=" + peerAddresses +
                '}';
    }

    /**
     * Represents the "xt" parameter.
     * E.g. xt=urn:btih:af0d9aa01a9ae123a73802cfa58ccaf355eb19f1
     *
     * @return Torrent ID
     * @since 1.3
     */
    public TorrentId getTorrentId() {
        return torrentId;
    }

    /**
     * Represents the "dn" parameter. Value is URL decoded.
     * E.g. dn=Some%20Display%20Name =&gt; "Some Display Name"
     *
     * @return Suggested display name for the threads.torrent
     * @since 1.3
     */
    public Optional<String> getDisplayName() {
        return Optional.ofNullable(displayName);
    }

    /**
     * Represents the collection of values of "x.pe" parameters. Values are URL decoded.
     * E.g. {@code x.pe=124.131.72.242%3A6891&x.pe=11.9.132.61%3A6900}
     * =&gt; [124.131.72.242:6891, 11.9.132.61:6900]
     *
     * @return Collection of well-known peer addresses
     * @since 1.3
     */
    public Collection<InetPeerAddress> getPeerAddresses() {
        return peerAddresses;
    }

    /**
     * @since 1.3
     */
    public static class Builder {
        private final TorrentId torrentId;
        private String displayName;
        private Collection<String> trackerUrls;
        private Collection<InetPeerAddress> peerAddresses;

        /**
         * @since 1.3
         */
        Builder(TorrentId torrentId) {
            this.torrentId = Objects.requireNonNull(torrentId);
        }

        /**
         * Set "dn" parameter.
         * Caller must NOT perform URL encoding, otherwise the value will get encoded twice.
         *
         * @param displayName Suggested display name
         * @since 1.3
         */
        public void name(String displayName) {
            this.displayName = Objects.requireNonNull(displayName);
        }

        /**
         * Add "tr" parameter.
         * Caller must NOT perform URL encoding, otherwise the value will get encoded twice.
         *
         * @param trackerUrl Tracker URL
         * @since 1.3
         */
        public void tracker(String trackerUrl) {
            Objects.requireNonNull(trackerUrl);
            if (trackerUrls == null) {
                trackerUrls = new HashSet<>();
            }
            trackerUrls.add(trackerUrl);
        }

        /**
         * Add "x.pe" parameter.
         *
         * @param peerAddress Well-known peer address
         * @since 1.3
         */
        public void peer(InetPeerAddress peerAddress) {
            Objects.requireNonNull(peerAddress);
            if (peerAddresses == null) {
                peerAddresses = new HashSet<>();
            }
            peerAddresses.add(peerAddress);
        }

        /**
         * @since 1.3
         */
        MagnetUri buildUri() {
            return new MagnetUri(torrentId, displayName, trackerUrls, peerAddresses);
        }
    }
}
