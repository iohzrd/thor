package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

import threads.thor.Settings;
import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.Key;

/*
 * We need to detect when the closest set is stable
 *  - in principle we're done as soon as there is no request candidates
 */
class ClosestSet {

    final int targetSize;
    private final NavigableSet<KBucketEntry> closest;
    private final Key target;

    int insertAttemptsSinceTailModification = 0;
    private int insertAttemptsSinceHeadModification = 0;


    ClosestSet(Key target) {
        this.target = target;
        closest = new ConcurrentSkipListSet<>(new KBucketEntry.DistanceOrder(target));
        this.targetSize = Settings.MAX_ENTRIES_PER_BUCKET;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean reachedTargetCapacity() {
        return closest.size() >= targetSize;
    }

    void insert(KBucketEntry reply) {
        synchronized (this) {
            closest.add(reply);
            if (closest.size() > targetSize) {
                KBucketEntry last = closest.last();
                closest.remove(last);
                if (last == reply)
                    insertAttemptsSinceTailModification++;
                else
                    insertAttemptsSinceTailModification = 0;
            }

            if (closest.first() == reply) {
                insertAttemptsSinceHeadModification = 0;
            } else {
                insertAttemptsSinceHeadModification++;
            }
        }
    }


    Stream<Key> ids() {
        return closest.stream().map(KBucketEntry::getID);
    }

    Stream<KBucketEntry> entries() {
        return closest.stream();
    }

    Key tail() {
        if (closest.isEmpty())
            return target.distance(Key.MAX_KEY);

        return closest.last().getID();
    }

    Key head() {
        if (closest.isEmpty())
            return target.distance(Key.MAX_KEY);
        return closest.first().getID();
    }

    @NonNull
    @Override
    public String toString() {
        String str = "closestset: " + closest.size() + " tailMod:" + insertAttemptsSinceTailModification +
                " headMod:" + insertAttemptsSinceHeadModification;
        str += " head:" + head().findApproxKeyDistance(target) + " tail:" + tail().findApproxKeyDistance(target);

        return str;

    }


}
