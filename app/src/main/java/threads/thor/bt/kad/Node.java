package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import threads.thor.BuildConfig;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.MessageBase.Type;
import threads.thor.bt.kad.tasks.PingRefreshTask;
import threads.thor.bt.kad.tasks.Task;
import threads.thor.bt.net.PeerId;
import threads.thor.bt.utils.CowSet;
import threads.thor.bt.utils.NetMask;
import threads.thor.bt.utils.Pair;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static threads.thor.bt.kad.Node.InsertOptions.ALWAYS_SPLIT_IF_FULL;
import static threads.thor.bt.kad.Node.InsertOptions.FORCE_INTO_MAIN_BUCKET;
import static threads.thor.bt.kad.Node.InsertOptions.NEVER_SPLIT;
import static threads.thor.bt.kad.Node.InsertOptions.RELAXED_SPLIT;
import static threads.thor.bt.kad.Node.InsertOptions.REMOVE_IF_FULL;


/**
 * @author Damokles
 */
public class Node {

    static final long throttleUpdateIntervalMinutes = 1;
    // -1 token per minute, 60 saturation, 30 threshold
    // if we see more than 1 per minute then it'll take 30 minutes until an unsolicited request can go into a replacement bucket again
    private static final long throttleIncrement = 10;
    /*
     * Verification Strategy:
     *
     * - trust incoming requests less than responses to outgoing requests
     * - most outgoing requests will have an expected ID - expected ID may come from external nodes, so don't take it at face value
     *  - if response does not match expected ID drop the packet for routing table accounting purposes without penalizing any existing routing table entry
     * - map routing table entries to IP addresses
     *  - verified responses trump unverified entries
     *  - lookup all routing table entry for incoming messages based on IP address (not node ID!) and ignore them if ID does not match
     *  - also ignore if port changed
     *  - drop, not just ignore, if we are sure that the incoming message is not fake (mtid-verified response)
     * - allow duplicate addresses for unverified entries
     *  - scrub later when one becomes verified
     * - never hand out unverified entries to other nodes
     *
     * other stuff to keep in mind:
     *
     * - non-reachable nodes may spam -> floods replacements -> makes it hard to get proper replacements without active lookups
     *
     */
    private static final long throttleSaturation = 60;
    private static final long throttleThreshold = 30;
    private final CowSet<Key> usedIDs = new CowSet<>();
    private final Object CoWLock = new Object();
    private final DHT dht;
    private final ConcurrentHashMap<InetAddress, Long> unsolicitedThrottle = new ConcurrentHashMap<>();
    private final Map<KBucket, Task> maintenanceTasks = new IdentityHashMap<>();
    private final Collection<NetMask> trustedNodes = Collections.emptyList();
    private volatile RoutingTable routingTableCOW = new RoutingTable();
    private final Runnable singleThreadedUpdateHomeBuckets = SerializedTaskExecutor.onceMore(this::updateHomeBuckets);
    private long timeOfLastPingCheck;
    private int num_entries;
    private Key baseKey;
    private volatile Map<InetAddress, RoutingTableEntry> knownNodes = new HashMap<>();
    private final Consumer<MessageBase> sequentialReceived = SerializedTaskExecutor.runSerialized(this::recievedConcurrent);

    public Node(DHT dht) {
        this.dht = dht;
    }

    void recieved(MessageBase msg) {
        sequentialReceived.accept(msg);
    }

    /**
     * An RPC message was received, the node must now update the right bucket.
     *
     * @param msg The message
     */
    private void recievedConcurrent(MessageBase msg) {
        InetAddress ip = msg.getOrigin().getAddress();
        Key id = msg.getID();

        Optional<RPCCall> associatedCall = Optional.ofNullable(msg.getAssociatedCall());
        Optional<Key> expectedId = associatedCall.map(RPCCall::getExpectedID);
        Optional<Pair<KBucket, KBucketEntry>> entryByIp = bucketForIP(ip);

        // RPCServer only verifies IP equality for responses.
        // we only want remote nodes with stable ports in our routing table, so appley a stricter check here
        if (associatedCall.isPresent() && !associatedCall.filter(c -> c.getRequest().getDestination().equals(c.getResponse().getOrigin())).isPresent()) {
            return;
        }

        if (entryByIp.isPresent()) {
            KBucket oldBucket = entryByIp.get().a;
            KBucketEntry oldEntry = entryByIp.get().b;

            // this might happen if
            // a) multiple nodes on a single IP -> ignore anything but the node we already have in the table
            // b) one node changes ports (broken NAT?) -> ignore until routing table entry times out
            if (oldEntry.getAddress().getPort() != msg.getOrigin().getPort())
                return;


            if (!oldEntry.getID().equals(id)) { // ID mismatch

                if (associatedCall.isPresent()) {
                    /*
                     *  we are here because:
                     *  a) a node with that IP is in our routing table
                     *  b) port matches too
                     *  c) the message is a response (mtid-verified)
                     *  d) the ID does not match our routing table entry
                     *
                     *  That means we are certain that the node either changed its node ID or does some ID-spoofing.
                     *  In either case we don't want it in our routing table
                     */

                    DHT.logInfo("force-removing routing table entry " + oldEntry + " because ID-change was detected; new ID:" + msg.getID());
                    oldBucket.removeEntryIfBad(oldEntry, true);

                    // might be pollution attack, check other entries in the same bucket too in case random pings can't keep up with scrubbing.
                    RPCServer srv = msg.getServer();
                    tryPingMaintenance(oldBucket, "checking sibling bucket entries after ID change was detected", srv, (t) -> t.checkGoodEntries(true));

                    if (oldEntry.verifiedReachable()) {
                        // old verified
                        // new verified
                        // -> probably misbehaving node. don't insert
                        return;
                    }

                    /*
                     *  old never verified
                     *  new verified
                     *  -> may allow insert, as if the old one has never been there
                     *
                     *  but still need to check expected ID match.
                     *  TODO: if this results in an insert then the known nodes list may be stale
                     */

                } else {

                    // new message is *not* a response -> not verified -> fishy due to ID mismatch -> ignore
                    return;
                }

            }


        }

        KBucket bucketById = routingTableCOW.entryForId(id).bucket;
        Optional<KBucketEntry> entryById = bucketById.findByIPorID(null, id);

        // entry is claiming the same ID as entry with different IP in our routing table -> ignore
        if (entryById.isPresent() && !entryById.get().getAddress().getAddress().equals(ip))
            return;

        // ID mismatch from call (not the same as ID mismatch from routing table)
        // it's fishy at least. don't insert even if it proves useful during a lookup
        if (!entryById.isPresent() && expectedId.isPresent() && !expectedId.get().equals(id))
            return;

        KBucketEntry newEntry = new KBucketEntry(msg.getOrigin(), id);
        msg.getVersion().ifPresent(newEntry::setVersion);

        // throttle the insert-attempts for unsolicited requests, update-only once they exceed the threshold
        // does not apply to responses
        if (!associatedCall.isPresent() && updateAndCheckThrottle(newEntry.getAddress().getAddress())) {
            refreshOnly(newEntry);
            return;
        }

        associatedCall.ifPresent(c -> {
            newEntry.signalResponse(c.getRTT());
            newEntry.mergeRequestTime(c.getSentTime());
        });


        // force trusted entry into the routing table (by splitting if necessary) if it passed all preliminary tests and it's not yet in the table
        // although we can only trust responses, anything else might be spoofed to clobber our routing table
        boolean trustedAndNotPresent = !entryById.isPresent() && msg.getType() == Type.RSP_MSG && trustedNodes.stream().anyMatch(mask -> mask.contains(ip));

        Set<InsertOptions> opts = EnumSet.noneOf(InsertOptions.class);
        if (trustedAndNotPresent)
            opts.addAll(EnumSet.of(FORCE_INTO_MAIN_BUCKET, REMOVE_IF_FULL));
        if (msg.getType() == Type.RSP_MSG)
            opts.add(RELAXED_SPLIT);

        insertEntry(newEntry, opts);

        // we already should have the bucket. might be an old one by now due to splitting
        // but it doesn't matter, we just need to update the entry, which should stay the same object across bucket splits
        if (msg.getType() == Type.RSP_MSG) {
            bucketById.notifyOfResponse(msg);
        }
    }

    /**
     * @return true if it should be throttled
     */
    private boolean updateAndCheckThrottle(InetAddress addr) {
        long oldVal = unsolicitedThrottle.merge(addr, throttleIncrement,
                (k, v) -> Math.min(v + throttleIncrement, throttleSaturation)) - throttleIncrement;

        return oldVal > throttleThreshold;
    }

    private Optional<Pair<KBucket, KBucketEntry>> bucketForIP(InetAddress addr) {
        return Optional.ofNullable(knownNodes.get(addr)).
                map(RoutingTableEntry::getBucket).flatMap(bucket ->
                bucket.findByIPorID(addr, null).map(Pair.of(bucket)));
    }


    private void refreshOnly(KBucketEntry toRefresh) {
        KBucket bucket = routingTableCOW.entryForId(toRefresh.getID()).getBucket();

        bucket.refresh(toRefresh);
    }

    private void insertEntry(KBucketEntry toInsert, Set<InsertOptions> opts) {
        if (usedIDs.contains(toInsert.getID()))
            return;


        if (!dht.getType().canUseSocketAddress(toInsert.getAddress()))
            throw new IllegalArgumentException("attempting to insert " + toInsert +
                    " expected address type: " +
                    dht.getType().PREFERRED_ADDRESS_TYPE.getSimpleName());


        Key nodeID = toInsert.getID();

        RoutingTable currentTable = routingTableCOW;
        RoutingTableEntry tableEntry = currentTable.entryForId(nodeID);

        while (!opts.contains(NEVER_SPLIT) && tableEntry.bucket.isFull() && (opts.contains(FORCE_INTO_MAIN_BUCKET) || toInsert.verifiedReachable()) && tableEntry.prefix.getDepth() < Key.KEY_BITS - 1) {
            if (!opts.contains(ALWAYS_SPLIT_IF_FULL) && !canSplit(tableEntry, toInsert, opts.contains(RELAXED_SPLIT)))
                break;

            splitEntry(currentTable, tableEntry);
            currentTable = routingTableCOW;
            tableEntry = currentTable.entryForId(nodeID);
        }

        int oldSize = tableEntry.bucket.getNumEntries();

        KBucketEntry toRemove = null;

        if (opts.contains(REMOVE_IF_FULL)) {
            toRemove = tableEntry.bucket.getEntries().stream().filter(e -> trustedNodes.stream().noneMatch(mask -> mask.contains(e.getAddress().getAddress()))).max(KBucketEntry.AGE_ORDER).orElse(null);
        }

        if (opts.contains(FORCE_INTO_MAIN_BUCKET))
            tableEntry.bucket.modifyMainBucket(toRemove, toInsert);
        else
            tableEntry.bucket.insertOrRefresh(toInsert);

        // add delta to the global counter. inaccurate, but will be rebuilt by the bucket checks
        num_entries += tableEntry.bucket.getNumEntries() - oldSize;

    }

    private boolean canSplit(RoutingTableEntry entry, KBucketEntry toInsert, boolean relaxedSplitting) {
        if (entry.homeBucket)
            return true;

        if (!relaxedSplitting)
            return false;

        Comparator<Key> comp = new Key.DistanceOrder(toInsert.getID());

        Key closestLocalId = usedIDs.stream().min(comp).orElseThrow(() -> new IllegalStateException("expected to find a local ID"));

        KClosestNodesSearch search = new KClosestNodesSearch(closestLocalId, DHTConstants.MAX_ENTRIES_PER_BUCKET, dht);

        search.filter = x -> true;

        search.fill();
        List<KBucketEntry> found = search.getEntries();

        if (found.size() < DHTConstants.MAX_ENTRIES_PER_BUCKET)
            return true;

        KBucketEntry max = found.get(found.size() - 1);

        return closestLocalId.threeWayDistance(max.getID(), toInsert.getID()) > 0;
    }

    private void splitEntry(RoutingTable expect, RoutingTableEntry entry) {
        synchronized (CoWLock) {
            RoutingTable current = routingTableCOW;
            if (current != expect)
                return;

            RoutingTableEntry a = new RoutingTableEntry(entry.prefix.splitPrefixBranch(false), new KBucket(), this::isLocalBucket);
            RoutingTableEntry b = new RoutingTableEntry(entry.prefix.splitPrefixBranch(true), new KBucket(), this::isLocalBucket);

            routingTableCOW = current.modify(Collections.singletonList(entry), Arrays.asList(a, b));

            // suppress recursive splitting to relinquish the lock faster. this method is generally called in a loop anyway
            for (KBucketEntry e : entry.bucket.getEntries())
                insertEntry(e, EnumSet.of(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET));
        }

        // replacements are less important, transfer outside lock
        for (KBucketEntry e : entry.bucket.getReplacementEntries())
            insertEntry(e, EnumSet.noneOf(InsertOptions.class));

    }

    public RoutingTable table() {
        return routingTableCOW;
    }


    Key getRootID() {
        return baseKey;
    }

    public boolean isLocalId(Key id) {
        return usedIDs.contains(id);
    }

    private boolean isLocalBucket(Prefix p) {
        return usedIDs.stream().anyMatch(p::isPrefixOf);
    }

    private Collection<Key> localIDs() {
        return usedIDs.snapshot();
    }

    public DHT getDHT() {
        return dht;
    }

    /**
     * Increase the failed queries count of the bucket entry we sent the message to
     */
    void onTimeout(RPCCall call) {
        // don't timeout anything if we don't have a connection
        if (isInSurvivalMode())
            return;
        if (!call.getRequest().getServer().isReachable())
            return;

        InetSocketAddress dest = call.getRequest().getDestination();

        if (call.getExpectedID() != null) {
            routingTableCOW.entryForId(call.getExpectedID()).bucket.onTimeout(dest);
        } else {
            RoutingTableEntry entry = knownNodes.get(dest.getAddress());
            if (entry != null)
                entry.bucket.onTimeout(dest);
        }
    }

    void decayThrottle() {
        unsolicitedThrottle.replaceAll((addr, i) -> i - 1);
        unsolicitedThrottle.values().removeIf(e -> e <= 0);

    }

    private boolean isInSurvivalMode() {
        return dht.getServerManager().getActiveServerCount() == 0;
    }

    void removeId(Key k) {
        usedIDs.remove(k);
        dht.getScheduler().execute(singleThreadedUpdateHomeBuckets);
    }

    void registerServer(RPCServer srv) {
        srv.onEnqueue(this::onOutgoingRequest);
    }

    private void onOutgoingRequest(RPCCall c) {
        Key expectedId = c.getExpectedID();
        if (expectedId == null)
            return;
        KBucket bucket = routingTableCOW.entryForId(expectedId).getBucket();
        bucket.findByIPorID(c.getRequest().getDestination().getAddress(), expectedId)
                .ifPresent(KBucketEntry::signalScheduledRequest);
        bucket.replacementsStream().filter(r -> r.getAddress().equals(c.getRequest().getDestination()))
                .findAny().ifPresent(KBucketEntry::signalScheduledRequest);
    }

    Key registerId() {
        int idx = 0;
        Key k;

        while (true) {
            k = getRootID().getDerivedKey(idx);
            if (usedIDs.add(k))
                break;
            idx++;
        }

        dht.getScheduler().execute(singleThreadedUpdateHomeBuckets);

        return k;
    }

    /**
     * Check if a buckets needs to be refreshed, and refresh if necessary.
     */
    void doBucketChecks(long now) {

        boolean survival = isInSurvivalMode();

        // don't spam the checks if we're not receiving anything.
        // we don't want to cause too many stray packets somewhere in a network
        if (survival && now - timeOfLastPingCheck < DHTConstants.BOOTSTRAP_MIN_INTERVAL)
            return;
        timeOfLastPingCheck = now;

        mergeBuckets();

        int newEntryCount = 0;

        for (RoutingTableEntry e : routingTableCOW.entries) {
            KBucket b = e.bucket;
            boolean isHome = e.homeBucket;

            List<KBucketEntry> entries = b.getEntries();

            Set<Key> localIds = usedIDs.snapshot();

            boolean wasFull = b.getNumEntries() >= DHTConstants.MAX_ENTRIES_PER_BUCKET;
            for (KBucketEntry entry : entries) {
                // remove really old entries, ourselves and bootstrap nodes if the bucket is full
                if (localIds.contains(entry.getID()) || (wasFull && dht.getBootStrapNodes().contains(entry.getAddress()))) {
                    b.removeEntryIfBad(entry, true);
                    continue;
                }


                // remove duplicate entries, keep the older one
                RoutingTableEntry reverseMapping = knownNodes.get(entry.getAddress().getAddress());
                if (reverseMapping != null && reverseMapping != e) {
                    KBucket otherBucket = reverseMapping.getBucket();
                    KBucketEntry other = otherBucket.findByIPorID(entry.getAddress().getAddress(), null).orElse(null);
                    if (other != null && !other.equals(entry)) {
                        if (other.getCreationTime() < entry.getCreationTime()) {
                            b.removeEntryIfBad(entry, true);
                        } else {
                            otherBucket.removeEntryIfBad(other, true);
                        }
                    }
                }

            }

            boolean refreshNeeded = b.needsToBeRefreshed();
            boolean replacementNeeded = b.needsReplacementPing() || (isHome && b.findPingableReplacement().isPresent());
            if (refreshNeeded || replacementNeeded)
                tryPingMaintenance(b, "Refreshing Bucket #" + e.prefix, null, PingRefreshTask::probeUnverifiedReplacement);

            if (!survival) {
                // only replace 1 bad entry with a replacement bucket entry at a time (per bucket)
                b.promoteVerifiedReplacement();
            }

            newEntryCount += e.bucket.getNumEntries();


        }

        num_entries = newEntryCount;

        rebuildAddressCache();
        decayThrottle();
    }

    private void tryPingMaintenance(KBucket b, String reason,
                                    RPCServer srv, Consumer<PingRefreshTask> taskConfig) {
        if (srv == null)
            srv = dht.getServerManager().getRandomActiveServer(true);

        if (maintenanceTasks.containsKey(b))
            return;


        if (srv != null) {
            PingRefreshTask prt = new PingRefreshTask(srv, this, null, false);

            if (taskConfig != null)
                taskConfig.accept(prt);
            prt.setInfo(reason);

            prt.addBucket(b);

            if (prt.getTodoCount() > 0 && maintenanceTasks.putIfAbsent(b, prt) == null) {
                prt.addListener(x -> maintenanceTasks.remove(b, prt));
                dht.getTaskManager().addTask(prt);
            }

        }
    }

    // TODO implement merges for non-home buckets that got split in the past
    private void mergeBuckets() {

        int i = 0;

        // perform bucket merge operations where possible
        while (true) {
            i++;
            if (i < 1)
                continue;

            // fine-grained locking to interfere less with other operations
            synchronized (CoWLock) {
                if (i >= routingTableCOW.size())
                    break;

                RoutingTableEntry e1 = routingTableCOW.get(i - 1);
                RoutingTableEntry e2 = routingTableCOW.get(i);

                if (e1.prefix.isSiblingOf(e2.prefix)) {
                    int effectiveSize1 = (int) (e1.getBucket().entriesStream().filter(e -> !e.removableWithoutReplacement()).count() + e1.getBucket().replacementsStream().filter(KBucketEntry::eligibleForNodesList).count());
                    int effectiveSize2 = (int) (e2.getBucket().entriesStream().filter(e -> !e.removableWithoutReplacement()).count() + e2.getBucket().replacementsStream().filter(KBucketEntry::eligibleForNodesList).count());

                    // uplift siblings if the other one is dead
                    if (effectiveSize1 == 0 || effectiveSize2 == 0) {
                        KBucket toLift = effectiveSize1 == 0 ? e2.getBucket() : e1.getBucket();

                        RoutingTable table = routingTableCOW;
                        routingTableCOW = table.modify(Arrays.asList(e1, e2),
                                Collections.singletonList(
                                        new RoutingTableEntry(e2.prefix.getParentPrefix(),
                                                toLift, this::isLocalBucket)));
                        i -= 2;
                        continue;
                    }

                    // check if the buckets can be merged without losing entries

                    if (effectiveSize1 + effectiveSize2 <= DHTConstants.MAX_ENTRIES_PER_BUCKET) {

                        RoutingTable table = routingTableCOW;
                        routingTableCOW = table.modify(Arrays.asList(e1, e2),
                                Collections.singletonList(new RoutingTableEntry(
                                        e1.prefix.getParentPrefix(),
                                        new KBucket(), this::isLocalBucket)));

                        // no splitting to avoid fibrillation between merge and split operations

                        for (KBucketEntry e : e1.bucket.getEntries())
                            insertEntry(e, EnumSet.of(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET));
                        for (KBucketEntry e : e2.bucket.getEntries())
                            insertEntry(e, EnumSet.of(InsertOptions.NEVER_SPLIT, InsertOptions.FORCE_INTO_MAIN_BUCKET));

                        e1.bucket.replacementsStream().forEach(r -> insertEntry(r, EnumSet.of(InsertOptions.NEVER_SPLIT)));
                        e2.bucket.replacementsStream().forEach(r ->
                                insertEntry(r, EnumSet.of(InsertOptions.NEVER_SPLIT))
                        );

                        i -= 2;

                    }
                }
            }

        }
    }

    private void updateHomeBuckets() {
        while (true) {
            RoutingTable t = table();
            List<RoutingTableEntry> changed = new ArrayList<>();
            for (int i = 0; i < t.size(); i++) {
                RoutingTableEntry e = t.get(i);
                // update home bucket status on local ID change
                if (isLocalBucket(e.prefix) != e.homeBucket)
                    changed.add(e);

            }

            synchronized (CoWLock) {
                if (routingTableCOW != t)
                    continue;
                if (changed.isEmpty())
                    break;
                routingTableCOW = t.modify(changed, changed.stream()
                        .map(e -> new RoutingTableEntry(e.prefix, e.bucket, this::isLocalBucket))
                        .collect(Collectors.toList()));
                break;
            }
        }

    }

    private void rebuildAddressCache() {
        Map<InetAddress, RoutingTableEntry> newKnownMap = new HashMap<>(num_entries);
        RoutingTable table = routingTableCOW;
        for (int i = 0, n = table.size(); i < n; i++) {
            RoutingTableEntry entry = table.get(i);
            Stream<KBucketEntry> entries = entry.bucket.entriesStream();
            entries.forEach(e -> newKnownMap.put(e.getAddress().getAddress(), entry));
        }

        knownNodes = newKnownMap;
    }

    /**
     * Check if a buckets needs to be refreshed, and refresh if necesarry
     */
    void fillBuckets() {
        RoutingTable table = routingTableCOW;

        for (int i = 0; i < table.size(); i++) {
            RoutingTableEntry entry = table.get(i);

            int num = entry.bucket.getNumEntries();

            // just try to fill partially populated buckets
            // not empty ones, they may arise as artifacts from deep splitting
            if (num > 0 && num < DHTConstants.MAX_ENTRIES_PER_BUCKET) {

                dht.fillBucket(entry.prefix.createRandomKeyFromPrefix(), entry.bucket, t -> t.setInfo("Filling Bucket #" + entry.prefix));
            }
        }
    }


    void initKey(@NonNull PeerId peerId) {
        baseKey = new Key(peerId.getBytes());
    }

    public int getNumEntriesInRoutingTable() {
        return num_entries;
    }

    Optional<KBucketEntry> getRandomEntry() {
        RoutingTable table = routingTableCOW;

        int offset = ThreadLocalRandom.current().nextInt(table.size());

        // sweep from a random offset in case there are empty buckets
        return IntStream.range(0, table.size()).mapToObj(i -> table.get((i + offset) % table.size())
                .getBucket().randomEntry()).filter(Optional::isPresent).map(Optional::get).findAny();
    }


    @Override
    @NonNull
    public String toString() {
        StringBuilder b = new StringBuilder(10000);

        try {
            buildDiagnistics(b);
        } catch (IOException e) {
            throw new Error("should not happen");
        }

        return b.toString();
    }

    void buildDiagnistics(Appendable b) throws IOException {
        RoutingTable table = routingTableCOW;

        Collection<Key> localIds = localIDs();

        b.append("buckets: ");
        b.append(String.valueOf(table.size()));
        b.append(" / entries: ");
        b.append(String.valueOf(num_entries));
        b.append('\n');
        for (RoutingTableEntry e : table.entries) {
            b.append(e.prefix.toString());
            b.append("   num:");
            b.append(String.valueOf(e.bucket.getNumEntries()));
            b.append(" rep:");
            b.append(String.valueOf(e.bucket.getNumReplacements()));
            if (localIds.stream().anyMatch(e.prefix::isPrefixOf))
                b.append(" [Home]");
            b.append('\n');
        }
    }

    enum InsertOptions {
        ALWAYS_SPLIT_IF_FULL,
        NEVER_SPLIT,
        RELAXED_SPLIT,
        REMOVE_IF_FULL,
        FORCE_INTO_MAIN_BUCKET
    }

    public static final class RoutingTableEntry implements Comparable<RoutingTableEntry> {

        public final Prefix prefix;
        final KBucket bucket;
        final boolean homeBucket;

        RoutingTableEntry(Prefix prefix, KBucket bucket, Predicate<Prefix> checkHome) {
            this.prefix = prefix;
            this.bucket = bucket;
            this.homeBucket = checkHome.test(prefix);
        }

        public KBucket getBucket() {
            return bucket;
        }

        public int compareTo(RoutingTableEntry o) {
            return prefix.compareTo(o.prefix);
        }

        @Override
        public String toString() {
            return prefix.toString() + " " + bucket.toString();
        }
    }

    public static final class RoutingTable {

        final RoutingTableEntry[] entries;
        final int[] indexCache;

        RoutingTable(RoutingTableEntry... entries) {
            this.entries = entries;
            if (entries.length > 64) {
                indexCache = buildCache();
            } else {
                indexCache = new int[]{0, entries.length};
            }

        }

        RoutingTable() {
            this(new RoutingTableEntry(new Prefix(), new KBucket(), (x) -> true));
        }

        int[] buildCache() {
            int[] cache = new int[256];

            if (BuildConfig.DEBUG && !(Integer.bitCount(cache.length) == 1)) {
                throw new AssertionError("Assertion failed");
            }

            int lsb = Integer.bitCount((cache.length / 2) - 1) - 1;

            Key increment = Key.setBit(lsb);
            Key trailingBits = new Prefix(Key.MAX_KEY, lsb).distance(Key.MAX_KEY);
            Key currentLower = new Key(new Prefix(Key.MIN_KEY, lsb));
            Key currentUpper = new Prefix(Key.MIN_KEY, lsb).distance(trailingBits);

            int innerOffset = 0;

            for (int i = 0; i < cache.length; i += 2) {
                cache[i + 1] = entries.length;

                for (int j = innerOffset; j < entries.length; j++) {
                    Prefix p = entries[j].prefix;

                    if (p.compareTo(currentLower) <= 0) {
                        innerOffset = cache[i] = max(cache[i], j);
                    }

                    if (p.compareTo(currentUpper) >= 0) {
                        cache[i + 1] = min(cache[i + 1], j);
                        break;
                    }

                }

                currentLower = new Key(new Prefix(currentLower.add(increment), lsb));
                currentUpper = currentLower.distance(trailingBits);
            }

            // System.out.println(IntStream.of(cache).mapToObj(Integer::toString).collect(Collectors.joining(", ")));

            return cache;
        }

        int indexForId(Key id) {
            int mask = indexCache.length / 2 - 1;
            int bits = Integer.bitCount(mask);

            int cacheIdx = id.getInt(0);

            cacheIdx = Integer.rotateLeft(cacheIdx, bits);
            cacheIdx = cacheIdx & mask;
            cacheIdx <<= 1;

            int lowerBound = indexCache[cacheIdx];
            int upperBound = indexCache[cacheIdx + 1];

            Prefix pivot;

            while (true) {
                int pivotIdx = (lowerBound + upperBound) >>> 1;
                pivot = entries[pivotIdx].prefix;

                if (pivotIdx == lowerBound)
                    break;

                if (pivot.compareTo(id) <= 0)
                    lowerBound = pivotIdx;
                else
                    upperBound = pivotIdx;
            }

            if (BuildConfig.DEBUG && !(pivot != null && pivot.isPrefixOf(id))) {
                throw new AssertionError("Assertion failed");
            }

            return lowerBound;
        }


        public RoutingTableEntry entryForId(Key id) {
            return entries[indexForId(id)];
        }

        public int size() {
            return entries.length;
        }

        public RoutingTableEntry get(int idx) {
            return entries[idx];
        }

        public List<RoutingTableEntry> list() {
            return Collections.unmodifiableList(Arrays.asList(entries));
        }

        RoutingTable modify(Collection<RoutingTableEntry> toRemove,
                            Collection<RoutingTableEntry> toAdd) {
            List<RoutingTableEntry> temp = new ArrayList<>(Arrays.asList(entries));
            if (toRemove != null)
                temp.removeAll(toRemove);
            if (toAdd != null)
                temp.addAll(toAdd);
            return new RoutingTable(temp.stream().sorted().toArray(RoutingTableEntry[]::new));
        }

    }

}
