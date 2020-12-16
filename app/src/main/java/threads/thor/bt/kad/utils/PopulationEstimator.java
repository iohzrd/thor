package threads.thor.bt.kad.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import threads.LogUtils;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Prefix;
import threads.thor.bt.utils.ExponentialWeightendMovingAverage;


public class PopulationEstimator {
    private static final int KEYSPACE_BITS = Key.KEY_BITS;
    private static final int MAX_RAW_HISTORY = 40;
    private static final String TAG = PopulationEstimator.class.getSimpleName();
    private static final int MAX_RECENT_LOOKUP_CACHE_SIZE = 40;
    private final Deque<Prefix> recentlySeenPrefixes = new LinkedList<>();
    private final LinkedList<Double> rawDistances = new LinkedList<>();
    private final ExponentialWeightendMovingAverage errorEstimate = new ExponentialWeightendMovingAverage().setWeight(0.03).setValue(0.5);
    private final ExponentialWeightendMovingAverage averageNodeDistanceExp2 = new ExponentialWeightendMovingAverage().setValue(1);


    private static double distanceToDouble(Key a, Key b) {
        byte[] rawDistance = a.distance(b).getHash();
        double distance = 0;

        int nonZeroBytes = 0;
        for (int j = 0; j < Key.SHA1_HASH_LENGTH; j++) {
            if (rawDistance[j] == 0) {
                continue;
            }
            if (nonZeroBytes == 8) {
                break;
            }
            nonZeroBytes++;
            distance += (rawDistance[j] & 0xFF)
                    * Math.pow(2, KEYSPACE_BITS - (j + 1) * 8);
        }

        return distance;
    }


    public long getEstimate() {
        return (long) (Math.pow(2, averageNodeDistanceExp2.getAverage()));
    }

    private double toLog2(double value) {
        return 160 - Math.log(value) / Math.log(2);
    }

    private double median(List<Double> distances) {
        if (distances.size() == 1)
            return distances.get(0);
        double[] values = new double[distances.size()];
        int i = 0;
        for (Double d : distances)
            values[i++] = d;
        Arrays.sort(values);
        // use a weighted 2-element median for max. accuracy
        double middle = (values.length - 1.0) / 2.0;
        int idx1 = (int) Math.floor(middle);
        int idx2 = (int) Math.ceil(middle);
        double middleWeight = middle - idx1;
        return values[idx1] * (1.0 - middleWeight) + values[idx2] * middleWeight;
    }

    public void update(Set<Key> neighbors, Key target) {
        // need at least 2 elements to calculate distances
        if (neighbors.size() < 2)
            return;

        LogUtils.debug(TAG, "Estimator: new node group of " + neighbors.size());
        Prefix prefix = Prefix.getCommonPrefix(neighbors);

        synchronized (recentlySeenPrefixes) {
            for (Prefix oldPrefix : recentlySeenPrefixes) {
                if (oldPrefix.isPrefixOf(prefix)) {
                    /*
                     * displace old entry, narrower entries will also replace
                     * wider ones, to clean out accidents like prefixes covering
                     * huge fractions of the keyspace
                     */
                    recentlySeenPrefixes.remove(oldPrefix);
                    recentlySeenPrefixes.addLast(prefix);
                    return;
                }
                // new prefix is wider than the old one, return but do not displace
                if (prefix.isPrefixOf(oldPrefix))
                    return;
            }

            // no match found => add
            recentlySeenPrefixes.addLast(prefix);
            if (recentlySeenPrefixes.size() > MAX_RECENT_LOOKUP_CACHE_SIZE)
                recentlySeenPrefixes.removeFirst();
        }


        ArrayList<Key> found = new ArrayList<>(neighbors);
        //found.add(target);
        found.sort(new Key.DistanceOrder(target));

        synchronized (PopulationEstimator.class) {

            List<Double> distances = new LinkedList<>();

            for (int i = 1; i < found.size(); i++) {
                distances.add(distanceToDouble(target, found.get(i)) - distanceToDouble(target, found.get(i - 1)));
                //distances.add(distanceToDouble(found.get(i-1), found.get(i)));
                //distances.add(distanceToDouble(target, found.get(i))/(i+1.0));
                //distances.add(target.naturalDistance(found.get(i)));
            }

            //System.out.println(distances);

            // distances are exponentially distributed. since we're taking the median we need to compensate here
            double median = median(distances) / Math.log(2);

            // work in log2 space for better averaging
            median = toLog2(median);
            //143.39035952556318
            //143.255
            //0.135

            LogUtils.debug(TAG, "Estimator: distance value: " +
                    median + " avg:" + averageNodeDistanceExp2);

            double absArror = Math.abs(errorEstimate.getAverage());
            double amplifiedError = Math.pow(absArror, 1.5);
            double clampedError = Math.max(0, Math.min(1, amplifiedError));


            double weight = 0.0001 + clampedError * 0.3;   //updateCount++ < INITIAL_UPDATE_COUNT ? DISTANCE_WEIGHT_INITIAL : DISTANCE_WEIGHT;
            //double weight = 0.001;

            // exponential average of the mean value
            averageNodeDistanceExp2.setWeight(weight).updateAverage(median);

            double newAverage = averageNodeDistanceExp2.getAverage();

            //System.out.print("update: "+ Math.pow(2, KEYSPACE_BITS - median)+" ");

            errorEstimate.updateAverage((median - newAverage) / Math.min(median, newAverage));


            while (rawDistances.size() > MAX_RAW_HISTORY)
                rawDistances.remove();
        }

        LogUtils.info(TAG,
                "Estimator: new estimate:" + getEstimate() +
                        " raw:" + averageNodeDistanceExp2.getAverage() +
                        " error:" + errorEstimate.getAverage());

    }

}
