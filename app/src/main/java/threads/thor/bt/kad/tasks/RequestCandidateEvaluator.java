package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import threads.thor.bt.kad.KBucketEntry;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCState;
import threads.thor.bt.kad.tasks.Task.RequestPermit;

/* algo:
 * 1. check termination condition
 * 2. allow if free slot
 * 3. if stall slot check
 * a) is candidate better than non-stalled in flight
 * b) is candidate better than head (homing phase)
 * c) is candidate better than tail (stabilizing phase)
 */

class RequestCandidateEvaluator {

    private final Task t;
    private final Key target;
    private final ClosestSet closest;
    private final KBucketEntry candidate;
    private final Collection<RPCCall> inFlight;
    private final IterativeLookupCandidates todo;

    public RequestCandidateEvaluator(TargetedTask t, ClosestSet c, IterativeLookupCandidates todo, KBucketEntry cand, Collection<RPCCall> inFlight) {
        Objects.requireNonNull(cand);
        this.t = t;
        this.todo = todo;
        this.target = t.getTargetKey();
        this.closest = c;
        this.candidate = cand;
        this.inFlight = inFlight;
    }

    private Stream<Key> activeInFlight() {
        return inFlight.stream().filter(c -> {
            RPCState state = c.state();
            return state == RPCState.UNSENT || state == RPCState.SENT;
        }).map(RPCCall::getExpectedID);
    }

    private boolean inStabilization() {
        int[] suggestedCounts = closest.entries().mapToInt((k) -> todo.nodeForEntry(k).sources.size()).toArray();

        return Arrays.stream(suggestedCounts).anyMatch(i -> i >= 5) || Arrays.stream(suggestedCounts).filter(i -> i >= 4).count() >= 2;
    }

    private boolean candidateAheadOfClosestSet() {
        return !closest.reachedTargetCapacity() || target.threeWayDistance(closest.head(), candidate.getID()) > 0;
    }

    private boolean candidateAheadOfClosestSetTail() {
        return !closest.reachedTargetCapacity() || target.threeWayDistance(closest.tail(), candidate.getID()) > 0;
    }

    public boolean terminationPrecondition() {
        return !candidateAheadOfClosestSetTail() && (inStabilization() || closest.insertAttemptsSinceTailModification > closest.targetSize);
    }

    @NonNull
    @Override
    public String toString() {
        return t.age().toMillis() + " " + t.counts + " " + closest + " cand:" + candidate.getID().findApproxKeyDistance(target);
    }


    public boolean goodForRequest(RequestPermit p) {
        if (p == RequestPermit.NONE_ALLOWED)
            return false;

        boolean result = false;

        if (candidateAheadOfClosestSet())
            result = true;


        if (candidateAheadOfClosestSetTail() && inStabilization())
            result = true;
        if (!terminationPrecondition() && activeInFlight().count() == 0)
            result = true;

        //if(result)
        //	System.out.println("\n" + t.age()  + " " + t.counts+ "\n"+((IteratingTask)t).closestDebug());

        return result;

    }


}
