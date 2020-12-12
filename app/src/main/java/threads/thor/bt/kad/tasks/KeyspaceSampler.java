package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import threads.LogUtils;
import threads.thor.bt.kad.DHT.DHTtype;
import threads.thor.bt.kad.DHTConstants;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.KClosestNodesSearch;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.Prefix;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.SampleRequest;
import threads.thor.bt.kad.messages.SampleResponse;
import threads.thor.bt.kad.utils.AddressUtils;

public class KeyspaceSampler extends Task {

    private final Prefix range;
    private final NavigableMap<Key, Bucket> rt = new TreeMap<>();
    private final BiConsumer<RPCCall, Key> ihcallback;
    int maxDepth;
    private volatile Key cursor;
    private int compatibleReplies = 0;


    public KeyspaceSampler(RPCServer rpc, Node node, Prefix range, NodeLookup seed, BiConsumer<RPCCall, Key> callback) {
        super(rpc, node);

        this.range = range;

        Bucket root = new Bucket();
        root.p = new Prefix();
        rt.put(root.p, root);

        cursor = range.first();


        if (!seed.getTargetKey().equals(range.first()))
            throw new IllegalArgumentException("seed must target " + range.first());
        if (!seed.isFinished())
            throw new IllegalArgumentException("seed lookup must have finished");
        seed.closest.entries().forEach(this::insertCandidate);


        this.ihcallback = callback;
    }

    private void insertCandidate(KBucketEntry kbe) {
        synchronized (rt) {
            Bucket b = rt.floorEntry(kbe.getID()).getValue();

            if (b.replied.size() >= DHTConstants.MAX_ENTRIES_PER_BUCKET && b.p.last().compareTo(cursor) < 0)
                return;

            if (!b.visited.contains(kbe) && !b.replied.contains(kbe))
                b.candidates.add(kbe);
        }
    }

    private void insertVisisted(KBucketEntry kbe) {
        synchronized (rt) {
            Bucket b = rt.floorEntry(kbe.getID()).getValue();
            b.visited.add(kbe);
            b.candidates.remove(kbe);
        }
    }

    private void insertReplied(KBucketEntry kbe) {
        synchronized (rt) {
            Entry<Key, Bucket> e = rt.floorEntry(kbe.getID());
            Bucket target = e.getValue();

            target.candidates.remove(kbe);

            if (target.replied.size() >= DHTConstants.MAX_ENTRIES_PER_BUCKET && target.p.last().compareTo(cursor) < 0)
                return;
            target.replied.add(kbe);

            if (target.replied.size() > DHTConstants.MAX_ENTRIES_PER_BUCKET)
                split(target);

        }
    }

    private void split(Bucket target) {
        if (!target.p.splittable())
            return;
        synchronized (rt) {
            Bucket a = new Bucket();
            Bucket b = new Bucket();
            a.p = target.p.splitPrefixBranch(false);
            b.p = target.p.splitPrefixBranch(true);
            rt.remove(target.p);
            rt.put(a.p, a);
            rt.put(b.p, b);
            for (KBucketEntry toMove : target.candidates) {
                insertCandidate(toMove);
            }
            for (KBucketEntry toMove : target.visited) {
                insertVisisted(toMove);
            }
            for (KBucketEntry toMove : target.replied) {
                insertReplied(toMove);
            }
        }
    }

    @Override
    void update() {
        while (canDoRequest() && !isDone()) {
            advanceCursor();

            synchronized (rt) {
                Key first = rt.floorKey(cursor);
                Key last = range.last();

                if (first.compareTo(last) >= 0)
                    break;

                Optional<Entry<Key, Bucket>> opt = rt.subMap(first, last).entrySet().stream().filter(e -> !e.getValue().candidates.isEmpty()).findFirst();
                if (!opt.isPresent())
                    break;
                Entry<Key, Bucket> e = opt.get();
                Bucket b = e.getValue();

                Optional<KBucketEntry> kbeo = b.candidates.stream().min(new KBucketEntry.DistanceOrder(cursor));
                if (!kbeo.isPresent())
                    break;
                KBucketEntry kbe = kbeo.get();

                if (node.getDHT().getMismatchDetector().isIdInconsistencyExpected(kbe.getAddress(), kbe.getID()) || node.getDHT().getUnreachableCache().getFailures(kbe.getAddress()) > 1 || rpc.getRequestThrottle().test(kbe.getAddress().getAddress())) {
                    b.candidates.remove(kbe);
                    continue;
                }

                Key target = b.p.createRandomKeyFromPrefix();

                if (b.p.first().compareTo(range.first()) < 0 || b.p.last().compareTo(range.last()) > 0)
                    target = range.createRandomKeyFromPrefix();

                SampleRequest req = new SampleRequest(target);

                req.setDestination(kbe.getAddress());
                req.setWant4(rpc.getDHT().getType() == DHTtype.IPV4_DHT);
                req.setWant6(rpc.getDHT().getType() == DHTtype.IPV6_DHT);

                rpcCall(req, kbe.getID(), (c) -> {
                    c.builtFromEntry(kbe);
                    b.candidates.remove(kbe);
                    b.visited.add(kbe);
                });


            }
        }

    }

    private void advanceCursor() {
        while (cursor.compareTo(range.last()) < 0) {
            synchronized (rt) {
                Entry<Key, Bucket> e = rt.floorEntry(cursor);
                Bucket bucket = e.getValue();
                if (!bucket.candidates.isEmpty())
                    break;
                if (inFlight.stream().anyMatch(c -> {
                    Prefix bucketPrefix = bucket.p;
                    return bucketPrefix.isPrefixOf(((SampleRequest) c.getRequest()).getTarget()) || bucketPrefix.isPrefixOf(c.getExpectedID());
                })) {
                    break;
                }
                cursor = Optional.ofNullable(rt.higherKey(cursor)).orElse(range.last());
                populate(cursor);
            }
        }

        Key mergeCursor = Key.MIN_KEY;

        while (mergeCursor.compareTo(cursor) < 0) {
            synchronized (rt) {
                Entry<Key, Bucket> left = rt.floorEntry(mergeCursor);
                Entry<Key, Bucket> right = rt.higherEntry(mergeCursor);
                if (left == null || right == null)
                    break;
                if (right.getKey().compareTo(cursor) >= 0)
                    break;

                Bucket lb = left.getValue();
                Bucket rb = left.getValue();

                if (lb.p.isSiblingOf(rb.p)) {
                    rt.remove(left.getKey());
                    rt.remove(right.getKey());

                    Bucket merged = new Bucket();
                    merged.p = lb.p.getParentPrefix();

                    rt.put(merged.p, merged);

                    // TODO: do we need to transfer?

                    continue;
                }

                mergeCursor = right.getKey();
            }
        }

    }

    @Override
    void callFinished(RPCCall c, MessageBase rsp) {
        if (!c.matchesExpectedID())
            return;

        SampleResponse sam = (SampleResponse) rsp;

        if (sam.remoteSupportsSampling())
            compatibleReplies++;


        for (KBucketEntry kbe : (Iterable<KBucketEntry>) sam.getNodes(node.getDHT().getType()).entries()::iterator) {
            if (AddressUtils.isBogon(kbe.getAddress()))
                return;
            if (kbe.getID().compareTo(cursor) < 0)
                return;
            insertCandidate(kbe);

        }

        insertReplied(new KBucketEntry(rsp.getOrigin(), rsp.getID()));

        sam.getSamples().forEach(k -> ihcallback.accept(c, k));
    }

    @Override
    void callTimeout(RPCCall c) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getTodoCount() {
        synchronized (rt) {
            return rt.values().stream().mapToInt(b -> b.candidates.size()).sum();
        }
    }

    @Override
    protected boolean isDone() {
        return cursor.compareTo(range.last()) >= 0;
    }

    private void populate(Key k) {
        KClosestNodesSearch kns = new KClosestNodesSearch(k, DHTConstants.MAX_ENTRIES_PER_BUCKET, node.getDHT());
        kns.filter = KBucketEntry::eligibleForLocalLookup;
        kns.fill();
        kns.getEntries().forEach(this::insertCandidate);

        node.getDHT().getCache().get(k, DHTConstants.MAX_ENTRIES_PER_BUCKET).forEach(this::insertCandidate);
    }

    @Override
    public void start() {

        populate(range.first());


        this.addListener(t -> LogUtils.info(TAG, "SamplingCrawl done "));

        super.start();
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " prefix:" + range + " cursor:" + cursor + " buck:" + rt.size() + " supported:" + compatibleReplies;
    }

    static class Bucket {
        final List<KBucketEntry> replied = new ArrayList<>();
        final Set<KBucketEntry> visited = new HashSet<>();
        final Set<KBucketEntry> candidates = new HashSet<>();
        Prefix p;
    }


}
