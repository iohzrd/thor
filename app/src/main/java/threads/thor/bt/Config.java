package threads.thor.bt;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

import threads.thor.bt.net.InetPeerAddress;
import threads.thor.bt.net.PeerId;
import threads.thor.bt.protocol.crypto.EncryptionPolicy;
import threads.thor.bt.service.NetworkUtil;

public class Config {

    private final InetAddress acceptorAddress;
    private final Duration peerDiscoveryInterval;
    private final Duration peerHandshakeTimeout;
    private final Duration peerConnectionInactivityThreshold;
    private final int maxPeerConnections;
    private final int maxPeerConnectionsPerTorrent;
    private final int transferBlockSize;
    private final int maxIOQueueSize;
    private final int maxConcurrentlyActivePeerConnectionsPerTorrent;
    private final Duration maxPieceReceivingTime;
    private final Duration maxMessageProcessingInterval;
    private final Duration unreachablePeerBanDuration;
    private final int maxPendingConnectionRequests;
    private final Duration timeoutedAssignmentPeerBanDuration;
    private final EncryptionPolicy encryptionPolicy;
    private final int metadataExchangeBlockSize;
    private final int metadataExchangeMaxSize;
    private final int msePrivateKeySize;

    private final int maxOutstandingRequests;
    private final int networkBufferSize;
    private final Collection<InetPeerAddress> publicBootstrapNodes;
    private final Duration shutdownHookTimeout;
    private int acceptorPort;
    private int numOfHashingThreads;
    private PeerId localPeerId;


    public Config() {
        this.acceptorAddress = NetworkUtil.getInetAddressFromNetworkInterfaces();
        this.acceptorPort = 6891;
        this.peerDiscoveryInterval = Duration.ofSeconds(5);


        this.peerHandshakeTimeout = Duration.ofSeconds(30);
        this.peerConnectionInactivityThreshold = Duration.ofMinutes(3);
        this.maxPeerConnections = 500;
        this.maxPeerConnectionsPerTorrent = maxPeerConnections; // assume single threads.torrent per runtime by default; change this to (maxActive * 2) maybe?
        this.transferBlockSize = 16 * 1024; // 16 KB

        this.maxIOQueueSize = Integer.MAX_VALUE;
        this.shutdownHookTimeout = Duration.ofSeconds(30);
        this.numOfHashingThreads = 1; // do not parallelize by default
        this.maxConcurrentlyActivePeerConnectionsPerTorrent = 10;
        this.maxPieceReceivingTime = Duration.ofSeconds(5);
        this.maxMessageProcessingInterval = Duration.ofMillis(100);
        this.unreachablePeerBanDuration = Duration.ofMinutes(30);
        this.maxPendingConnectionRequests = 50;
        this.timeoutedAssignmentPeerBanDuration = Duration.ofMinutes(1);
        this.encryptionPolicy = EncryptionPolicy.PREFER_PLAINTEXT;
        this.metadataExchangeBlockSize = 16 * 1024; // 16 KB
        this.metadataExchangeMaxSize = 2 * 1024 * 1024; // 2 MB
        this.msePrivateKeySize = 20; // 20 bytes
        this.maxOutstandingRequests = 250;
        this.networkBufferSize = 1024 * 1024; // 1 MB


        this.publicBootstrapNodes = Arrays.asList(
                new InetPeerAddress("router.bittorrent.com", 6881),
                new InetPeerAddress("dht.transmissionbt.com", 6881),
                new InetPeerAddress("router.utorrent.com", 6881)
        );
    }


    /*public Config(Config config) {
        this.acceptorAddress = config.getAcceptorAddress();
        this.acceptorPort = config.getAcceptorPort();
        this.peerDiscoveryInterval = config.getPeerDiscoveryInterval();
        this.peerConnectionRetryInterval = config.getPeerConnectionRetryInterval();

        this.peerConnectionTimeout = config.getPeerConnectionTimeout();
        this.peerHandshakeTimeout = config.getPeerHandshakeTimeout();
        this.peerConnectionInactivityThreshold = config.getPeerConnectionInactivityThreshold();
        this.trackerQueryInterval = config.getTrackerQueryInterval();
        this.maxPeerConnections = config.getMaxPeerConnections();
        this.maxPeerConnectionsPerTorrent = config.getMaxPeerConnectionsPerTorrent();
        this.transferBlockSize = config.getTransferBlockSize();
        this.maxTransferBlockSize = config.getMaxTransferBlockSize();
        this.maxIOQueueSize = config.getMaxIOQueueSize();
        this.shutdownHookTimeout = config.getShutdownHookTimeout();
        this.numOfHashingThreads = config.getNumOfHashingThreads();
        this.maxConcurrentlyActivePeerConnectionsPerTorrent = config.getMaxConcurrentlyActivePeerConnectionsPerTorrent();
        this.maxPieceReceivingTime = config.getMaxPieceReceivingTime();
        this.maxMessageProcessingInterval = config.getMaxMessageProcessingInterval();
        this.unreachablePeerBanDuration = config.getUnreachablePeerBanDuration();
        this.maxPendingConnectionRequests = config.getMaxPendingConnectionRequests();
        this.timeoutedAssignmentPeerBanDuration = config.getTimeoutedAssignmentPeerBanDuration();
        this.encryptionPolicy = config.getEncryptionPolicy();
        this.metadataExchangeBlockSize = config.getMetadataExchangeBlockSize();
        this.metadataExchangeMaxSize = config.getMetadataExchangeMaxSize();
        this.msePrivateKeySize = config.getMsePrivateKeySize();
        this.numberOfPeersToRequestFromTracker = config.getNumberOfPeersToRequestFromTracker();
        this.maxOutstandingRequests = config.getMaxOutstandingRequests();
        this.networkBufferSize = config.getNetworkBufferSize();
    }*/

    @NonNull
    public PeerId getLocalPeerId() {
        return localPeerId;
    }

    public void setLocalPeerId(@NonNull PeerId localPeerId) {
        this.localPeerId = localPeerId;
    }

    /**
     * @return Collection of public bootstrap nodes (routers)
     * @since 1.3
     */
    public Collection<InetPeerAddress> getPublicBootstrapNodes() {
        return publicBootstrapNodes;
    }

    /**
     * @since 1.0
     */
    public InetAddress getAcceptorAddress() {
        return acceptorAddress;
    }

    /**
     * @since 1.0
     */
    public int getAcceptorPort() {
        return acceptorPort;
    }

    /**
     * @param acceptorPort Local port that will be used by the incoming connection acceptor.
     * @since 1.0
     */
    public void setAcceptorPort(int acceptorPort) {
        this.acceptorPort = acceptorPort;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerDiscoveryInterval() {
        return peerDiscoveryInterval;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerHandshakeTimeout() {
        return peerHandshakeTimeout;
    }

    /**
     * @since 1.0
     */
    public Duration getPeerConnectionInactivityThreshold() {
        return peerConnectionInactivityThreshold;
    }


    /**
     * @since 1.0
     */
    public int getMaxPeerConnections() {
        return maxPeerConnections;
    }

    /**
     * @since 1.0
     */
    public int getMaxPeerConnectionsPerTorrent() {
        return maxPeerConnectionsPerTorrent;
    }

    /**
     * @since 1.0
     */
    public int getTransferBlockSize() {
        return transferBlockSize;
    }

    /**
     * @since 1.0
     */
    public int getMaxIOQueueSize() {
        return maxIOQueueSize;
    }

    /**
     * @since 1.0
     */
    public Duration getShutdownHookTimeout() {
        return shutdownHookTimeout;
    }


    /**
     * @since 1.1
     */
    public int getNumOfHashingThreads() {
        return numOfHashingThreads;
    }

    /**
     * @param numOfHashingThreads Set this value to 2 or greater,
     *                            if verification of the threads.torrent data should be parallelized
     * @since 1.1
     */
    public void setNumOfHashingThreads(int numOfHashingThreads) {
        this.numOfHashingThreads = numOfHashingThreads;
    }

    /**
     * @since 1.1
     */
    public int getMaxConcurrentlyActivePeerConnectionsPerTorrent() {
        return maxConcurrentlyActivePeerConnectionsPerTorrent;
    }

    /**
     * @since 1.1
     */
    public Duration getMaxPieceReceivingTime() {
        return maxPieceReceivingTime;
    }

    /**
     * @since 1.1
     */
    public Duration getMaxMessageProcessingInterval() {
        return maxMessageProcessingInterval;
    }

    /**
     * @since 1.1
     */
    public Duration getUnreachablePeerBanDuration() {
        return unreachablePeerBanDuration;
    }

    /**
     * @since 1.1
     */
    public int getMaxPendingConnectionRequests() {
        return maxPendingConnectionRequests;
    }

    /**
     * @since 1.1
     */
    public Duration getTimeoutedAssignmentPeerBanDuration() {
        return timeoutedAssignmentPeerBanDuration;
    }

    /**
     * @since 1.2
     */
    public EncryptionPolicy getEncryptionPolicy() {
        return encryptionPolicy;
    }

    /**
     * @since 1.3
     */
    public int getMetadataExchangeBlockSize() {
        return metadataExchangeBlockSize;
    }

    /**
     * @since 1.3
     */
    public int getMetadataExchangeMaxSize() {
        return metadataExchangeMaxSize;
    }

    /**
     * @since 1.3
     */
    public int getMsePrivateKeySize() {
        return msePrivateKeySize;
    }


    /**
     * @since 1.9
     */
    public int getMaxOutstandingRequests() {
        return maxOutstandingRequests;
    }

    /**
     * @since 1.9
     */
    public int getNetworkBufferSize() {
        return networkBufferSize;
    }

}
