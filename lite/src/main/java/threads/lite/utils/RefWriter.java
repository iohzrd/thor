package threads.lite.utils;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.format.Link;
import threads.lite.format.Node;


public class RefWriter {


    private final Hashtable<String, Integer> seen = new Hashtable<>();
    private final List<Cid> cids = new ArrayList<>();
    private final boolean unique;
    private final int maxDepth;

    public RefWriter(boolean unique, int maxDepth) {
        this.unique = unique;
        this.maxDepth = maxDepth;
    }

    public void evalRefs(@NonNull Node top) {
        evalRefsRecursive(top, 0);
    }

    public List<Cid> getCids() {
        return cids;
    }


    public int evalRefsRecursive(@NonNull Node node, int depth) {

        int count = 0;
        List<Link> links = node.getLinks();
        for (Link link : links) {
            Cid lc = link.getCid();
            Pair<Boolean, Boolean> visited = visit(lc, depth + 1); // The children are at depth+1
            boolean goDeeper = visited.first;
            boolean shouldWrite = visited.second;

            // Avoid "Get()" on the node and continue with next Link.
            // We can do this if:
            // - We printed it before (thus it was already seen and
            //   fetched with Get()
            // - AND we must not go deeper.
            // This is an optimization for pruned branches which have been
            // visited before.
            if (!shouldWrite && !goDeeper) {
                continue;
            }

            // We must Get() the node because:
            // - it is new (never written)
            // - OR we need to go deeper.
            // This ensures printed refs are always fetched.


            // Write this node if not done before (or !Unique)
            if (shouldWrite) {
                cids.add(lc);
                count++;
            }

            // Keep going deeper. This happens:
            // - On unexplored branches
            // - On branches not explored deep enough
            // Note when !Unique, branches are always considered
            // unexplored and only depth limits apply.
           /*
           if(goDeeper) {
               int c = evalRefsRecursive(nd, depth + 1, enc);
               count += c;
           }*/
        }

        return count;
    }

    // visit returns two values:
    // - the first boolean is true if we should keep traversing the DAG
    // - the second boolean is true if we should print the CID
    //
    // visit will do branch pruning depending on rw.MaxDepth, previously visited
    // cids and whether rw.Unique is set. i.e. rw.Unique = false and
    // rw.MaxDepth = -1 disables any pruning. But setting rw.Unique to true will
    // prune already visited branches at the cost of keeping as set of visited
    // CIDs in memory.

    public Pair<Boolean, Boolean> visit(Cid c, int depth) {
        boolean atMaxDepth = maxDepth >= 0 && depth == maxDepth;
        boolean overMaxDepth = maxDepth >= 0 && depth > maxDepth;

        // Shortcut when we are over max depth. In practice, this
        // only applies when calling refs with --maxDepth=0, as root's
        // children are already over max depth. Otherwise nothing should
        // hit this.
        if (overMaxDepth) {
            return Pair.create(false, false);
        }

        // We can shortcut right away if we don't need unique output:
        //   - we keep traversing when not atMaxDepth
        //   - always print
        if (!unique) {
            return Pair.create(!atMaxDepth, true);
        }

        // Unique == true from this point.
        // Thus, we keep track of seen Cids, and their depth.

        String key = c.String();
        int oldDepth = 0;
        Integer value = seen.get(key);
        boolean ok = false;
        if (value != null) {
            ok = true;
            oldDepth = value;
        }


        // Unique == true && depth < MaxDepth (or unlimited) from this point

        // Branch pruning cases:
        // - We saw the Cid before and either:
        //   - Depth is unlimited (MaxDepth = -1)
        //   - We saw it higher (smaller depth) in the DAG (means we must have
        //     explored deep enough before)
        // Because we saw the CID, we don't print it again.
        if (ok && (maxDepth < 0 || oldDepth <= depth)) {
            return Pair.create(false, false);
        }

        // Final case, we must keep exploring the DAG from this CID
        // (unless we hit the depth limit).
        // We note down its depth because it was either not seen
        // or is lower than last time.
        // We print if it was not seen.
        seen.put(key, depth);
        return Pair.create(!atMaxDepth, !ok);
    }
}
