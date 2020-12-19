package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Formatter;

import threads.thor.Settings;
import threads.thor.bt.kad.messages.MessageBase;

import static threads.thor.bt.utils.Functional.tap;

public class ResponseTimeoutFilter {


    private static final int MIN_BIN = 0;
    private static final int MAX_BIN = Settings.RPC_CALL_TIMEOUT_MAX;
    private static final int BIN_SIZE = 50;
    private static final int NUM_BINS = (int) Math.ceil((MAX_BIN - MIN_BIN) * 1.0f / BIN_SIZE);


    private final float[] bins = new float[NUM_BINS];
    private volatile long updateCount;
    private Snapshot snapshot = new Snapshot(tap(bins.clone(), ary -> ary[ary.length - 1] = 1.0f));
    private long timeoutCeiling;
    private long timeoutBaseline;
    private final RPCCallListener listener = new RPCCallListener() {
        public void onTimeout(RPCCall c) {
        }

        public void onResponse(RPCCall c, MessageBase rsp) {
            updateAndRecalc(c.getRTT());
        }
    };

	/*
	public Map<RPCCall, Float> calculateFlightProbabilities(List<RPCCall> calls) {
		Collections.sort(calls,timeoutComp);
		long[] rtts = sortedRtts;
		Map<RPCCall, Float> result = new HashMap<>(calls.size());
		
		int prevRttIdx = 0;
		
		for(RPCCall c : calls)
		{
			while(prevRttIdx < rtts.length && rtts[prevRttIdx] < c.getRTT())
				prevRttIdx++;
			result.put(c, 1.0f * prevRttIdx / rtts.length);
		}
		
		return result;
	}*/

    public ResponseTimeoutFilter() {
        reset();
    }

    public void reset() {
        updateCount = 0;
        timeoutBaseline = timeoutCeiling = Settings.RPC_CALL_TIMEOUT_MAX;
        Arrays.fill(bins, 1.0f / bins.length);
    }

    public long getSampleCount() {
        return updateCount;
    }


    public void registerCall(final RPCCall call) {
        call.addListener(listener);
    }

    private void updateAndRecalc(long newRTT) {
        update(newRTT);
        if ((updateCount++ & 0x0f) == 0) {
            newSnapshot();
            decay();
        }
    }

    private void update(long newRTT) {
        int bin = (int) (newRTT - MIN_BIN) / BIN_SIZE;
        bin = Math.max(Math.min(bin, bins.length - 1), 0);

        bins[bin] += 1.0;
    }

    private void decay() {
        for (int i = 0; i < bins.length; i++) {
            bins[i] *= 0.95f;
        }
    }


    private void newSnapshot() {
        snapshot = new Snapshot(bins.clone());
        timeoutBaseline = (long) snapshot.getQuantile(0.1f);
        timeoutCeiling = (long) snapshot.getQuantile(0.9f);
    }

    public Snapshot getCurrentStats() {
        return snapshot;
    }

    public long getStallTimeout() {
        // either the 90th percentile or the 10th percentile + 100ms baseline, whichever is HIGHER (to prevent descent to zero and missing more than 10% of the packets in the worst case).
        // but At most RPC_CALL_TIMEOUT_MAX
        return Math.min(Math.max(timeoutBaseline + Settings.RPC_CALL_TIMEOUT_BASELINE_MIN, timeoutCeiling), Settings.RPC_CALL_TIMEOUT_MAX);
    }

    public static class Snapshot {
        final float[] values;

        float mean = 0;
        float mode = 0;


        Snapshot(float[] ary) {
            values = ary;

            normalize();

            calcStats();
        }

        void normalize() {
            float cumulativePopulation = 0;

            for (float value : values) {
                cumulativePopulation += value;
            }

            if (cumulativePopulation > 0)
                for (int i = 0; i < values.length; i++) {
                    values[i] /= cumulativePopulation;
                }

        }

        void calcStats() {
            float modePop = 0;

            for (int bin = 0; bin < values.length; bin++) {
                mean += values[bin] * (bin + 0.5f) * BIN_SIZE;
                if (values[bin] > modePop) {
                    mode = (bin + 0.5f) * BIN_SIZE;
                    modePop = values[bin];
                }

            }
        }

        float getQuantile(float quant) {
            for (int i = 0; i < values.length; i++) {
                quant -= values[i];
                if (quant <= 0)
                    return (i + 0.5f) * BIN_SIZE;
            }

            return MAX_BIN;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();

            b.append(" mean:").append(mean).append(" median:").append(getQuantile(0.5f)).append(" mode:").append(mode).append(" 10tile:").append(getQuantile(0.1f)).append(" 90tile:").append(getQuantile(0.9f));
            b.append('\n');

            Formatter l1 = new Formatter();
            Formatter l2 = new Formatter();
            for (int i = 0; i < values.length; i++) {
                if (values[i] >= 0.001) {
                    l1.format(" %5d | ", i * BIN_SIZE);
                    l2.format("%5d%% | ", Math.round(values[i] * 100));

                }

            }

            b.append(l1).append('\n');
            b.append(l2).append('\n');

            return b.toString();


        }

    }
}
