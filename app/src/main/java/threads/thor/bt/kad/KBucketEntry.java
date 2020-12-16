package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import threads.LogUtils;
import threads.thor.bt.utils.ExponentialWeightendMovingAverage;

import static threads.thor.bt.bencode.Utils.prettyPrint;

/**
 * Entry in a KBucket, it basically contains an ip_address of a node,
 * the udp port of the node and a node_id.
 *
 * @author Damokles
 */
public class KBucketEntry {

    /**
     * ascending order for timeCreated, i.e. the first value will be the oldest
     */
    static final Comparator<KBucketEntry> AGE_ORDER = Comparator.comparingLong(KBucketEntry::getCreationTime);
    private final static String TAG = KBucketEntry.class.getSimpleName();
    // 5 timeouts, used for exponential backoff as per kademlia paper
    private static final int MAX_TIMEOUTS = 5;
    // haven't seen it for a long time + timeout == evict sooner than pure timeout based threshold. e.g. for old entries that we haven't touched for a long time
    private static final int OLD_AND_STALE_TIME = 15 * 60 * 1000;
    private static final int PING_BACKOFF_BASE_INTERVAL = 60 * 1000;
    private static final int OLD_AND_STALE_TIMEOUTS = 2;
    private static final double RTT_EMA_WEIGHT = 0.3;


    private final InetSocketAddress addr;
    private final Key nodeID;
    private final byte[] v4_mask = {0x03, 0x0f, 0x3f, (byte) 0xff};
    private final byte[] v6_mask = {0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f, (byte) 0xff};
    private final ExponentialWeightendMovingAverage avgRTT = new ExponentialWeightendMovingAverage().setWeight(RTT_EMA_WEIGHT);
    private long lastSeen;
    private boolean verified = false;
    /**
     * -1 = never queried / learned about it from incoming requests
     * 0 = last query was a success
     * > 0 = query failed
     */
    private int failedQueries;
    private long timeCreated;
    private byte[] version;
    private long lastSendTime = -1;

    /**
     * Constructor, set the ip, port and key
     *
     * @param addr socket address
     * @param id   ID of node
     */
    public KBucketEntry(InetSocketAddress addr, Key id) {
        Objects.requireNonNull(addr);
        Objects.requireNonNull(id);
        lastSeen = System.currentTimeMillis();
        timeCreated = lastSeen;
        this.addr = addr;
        this.nodeID = id;
    }

    /**
     * Constructor, set the ip, port and key
     *
     * @param addr      socket address
     * @param id        ID of node
     * @param timestamp the timestamp when this node last responded
     */
    public KBucketEntry(InetSocketAddress addr, Key id, long timestamp) {
        Objects.requireNonNull(addr);
        Objects.requireNonNull(id);
        lastSeen = timestamp;
        timeCreated = System.currentTimeMillis();
        this.addr = addr;
        this.nodeID = id;
    }


    /**
     * @return the address of the node
     */
    public InetSocketAddress getAddress() {
        return addr;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof KBucketEntry)
            return this.equals((KBucketEntry) o);
        return false;
    }

    public boolean equals(KBucketEntry other) {
        if (other == null)
            return false;
        return nodeID.equals(other.nodeID) && addr.equals(other.addr);
    }

    public boolean matchIPorID(KBucketEntry other) {
        if (other == null)
            return false;
        return nodeID.equals(other.getID()) || addr.getAddress().equals(other.addr.getAddress());
    }

    @Override
    public int hashCode() {
        return nodeID.hashCode() + 1;
    }

    /**
     * @return id
     */
    public Key getID() {
        return nodeID;
    }

    /**
     * @return the version
     */
    public Optional<ByteBuffer> getVersion() {
        return Optional.ofNullable(version).map(ByteBuffer::wrap).map(ByteBuffer::asReadOnlyBuffer);
    }

    /**
     * @param version the version to set
     */
    public void setVersion(byte[] version) {
        this.version = version;
    }

    /**
     * @return the last_responded
     */
    public long getLastSeen() {
        return lastSeen;
    }

    public long getCreationTime() {
        return timeCreated;
    }

    @NonNull
    @Override
    public String toString() {
        long now = System.currentTimeMillis();
        StringBuilder b = new StringBuilder(80);
        b.append(nodeID).append("/").append(addr);
        if (lastSendTime > 0)
            b.append(";sent:").append(Duration.ofMillis(now - lastSendTime));
        b.append(";seen:").append(Duration.ofMillis(now - lastSeen));
        b.append(";age:").append(Duration.ofMillis(now - timeCreated));
        if (failedQueries != 0)
            b.append(";fail:").append(failedQueries);
        if (verified)
            b.append(";verified");
        double rtt = avgRTT.getAverage();
        if (!Double.isNaN(rtt))
            b.append(";rtt:").append(rtt);
        if (version != null)
            b.append(";ver:").append(prettyPrint(version));

        return b.toString();
    }

    public boolean eligibleForNodesList() {
        // 1 timeout can occasionally happen. should be fine to hand it out as long as we've verified it at least once
        return verifiedReachable() && failedQueries < 2;
    }

    public boolean eligibleForLocalLookup() {
        // allow implicit initial ping during lookups
        // TODO: make this work now that we don't keep unverified entries in the main bucket
        return (verifiedReachable() || failedQueries <= 0) && failedQueries <= 3;
    }

    public boolean verifiedReachable() {
        return verified;
    }

    public boolean neverContacted() {
        return lastSendTime == -1;
    }

    private int failedQueries() {
        return Math.abs(failedQueries);
    }

    private boolean withinBackoffWindow(long now) {
        int backoff = PING_BACKOFF_BASE_INTERVAL << Math.min(MAX_TIMEOUTS, Math.max(0, failedQueries() - 1));

        return failedQueries != 0 && now - lastSendTime < backoff;
    }

    public boolean needsPing() {
        long now = System.currentTimeMillis();

        // don't ping if recently seen to allow NAT entries to time out
        // see https://arxiv.org/pdf/1605.05606v1.pdf for numbers
        // and do exponential backoff after failures to reduce traffic
        if (now - lastSeen < 30 * 1000 || withinBackoffWindow(now))
            return false;

        return failedQueries != 0 || now - lastSeen > OLD_AND_STALE_TIME;
    }

    // old entries, e.g. from routing table reload
    private boolean oldAndStale() {
        return failedQueries > OLD_AND_STALE_TIMEOUTS && System.currentTimeMillis() - lastSeen > OLD_AND_STALE_TIME;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean removableWithoutReplacement() {
        // some non-reachable nodes may contact us repeatedly, bumping the last seen counter. they might be interesting to keep around so we can keep track of the backoff interval to not waste pings on them
        // but things we haven't heard from in a while can be discarded

        boolean seenSinceLastPing = lastSeen > lastSendTime;

        return failedQueries > MAX_TIMEOUTS && !seenSinceLastPing;
    }

    public boolean needsReplacement() {
        return (failedQueries > 1 && !verifiedReachable()) || failedQueries > MAX_TIMEOUTS || oldAndStale();
    }

    public void mergeInTimestamps(KBucketEntry other) {
        if (!this.equals(other) || this == other)
            return;
        lastSeen = Math.max(lastSeen, other.getLastSeen());
        lastSendTime = Math.max(lastSendTime, other.lastSendTime);
        timeCreated = Math.min(timeCreated, other.getCreationTime());
        if (other.verifiedReachable())
            setVerified();
        if (!Double.isNaN(other.avgRTT.getAverage()))
            avgRTT.updateAverage(other.avgRTT.getAverage());
    }

    public int getRTT() {
        return (int) avgRTT.getAverage(DHTConstants.RPC_CALL_TIMEOUT_MAX);
    }

    /**
     * @param rtt > 0 in ms. -1 if unknown
     */
    public void signalResponse(long rtt) {
        lastSeen = System.currentTimeMillis();
        failedQueries = 0;
        verified = true;
        if (rtt > 0)
            avgRTT.updateAverage(rtt);
    }

    public void mergeRequestTime(long requestSent) {
        lastSendTime = Math.max(lastSendTime, requestSent);
    }

    public void signalScheduledRequest() {
        lastSendTime = System.currentTimeMillis();
    }

    /**
     * Should be called to signal that a request to this peer has timed out;
     */
    public void signalRequestTimeout() {
        failedQueries++;
    }

    public boolean hasSecureID() {

        try {

            Checksum c = CRC32.class.newInstance();

            byte[] ip = getAddress().getAddress().getAddress();

            byte[] mask = ip.length == 4 ? v4_mask : v6_mask;

            for (int i = 0; i < mask.length; i++) {
                ip[i] &= mask[i];
            }

            int r = nodeID.getByte(19) & 0x7;

            ip[0] |= r << 5;

            c.reset();
            c.update(ip, 0, Math.min(ip.length, 8));
            int crc = (int) c.getValue();

            return ((nodeID.getInt(0) ^ crc) & 0xff_ff_f8_00) == 0;


			/*
			uint8_t* ip; // our external IPv4 or IPv6 address (network byte order)
			int num_octets; // the number of octets to consider in ip (4 or 8)
			uint8_t node_id[20]; // resulting node ID


			uint8_t* mask = num_octets == 4 ? v4_mask : v6_mask;

			for (int i = 0; i < num_octets; ++i)
			        ip[i] &= mask[i];

			uint32_t rand = std::rand() & 0xff;
			uint8_t r = rand & 0x7;
			ip[0] |= r << 5;

			uint32_t crc = 0;
			crc = crc32c(crc, ip, num_octets);

			// only take the top 21 bits from crc
			node_id[0] = (crc >> 24) & 0xff;
			node_id[1] = (crc >> 16) & 0xff;
			node_id[2] = ((crc >> 8) & 0xf8) | (std::rand() & 0x7);
			for (int i = 3; i < 19; ++i) node_id[i] = std::rand();
			node_id[19] = rand;
			*/

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            return false;
        }
    }

    private void setVerified() {
        verified = true;
    }

    public static final class DistanceOrder implements Comparator<KBucketEntry> {

        final Key target;

        public DistanceOrder(Key target) {
            this.target = target;
        }

        public int compare(KBucketEntry o1, KBucketEntry o2) {
            return target.threeWayDistance(o1.getID(), o2.getID());
        }
    }
}
