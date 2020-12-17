package threads.thor.bt.kad;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import threads.LogUtils;

public class SpamThrottle {
    private static final String TAG = SpamThrottle.class.getSimpleName();
    private static final int BURST = 10;
    private static final int PER_SECOND = 2;
    private final Map<InetAddress, Integer> hitcounter = new ConcurrentHashMap<>();
    private final AtomicLong lastDecayTime = new AtomicLong(System.currentTimeMillis());

    boolean addAndTest(InetAddress addr) {
        int updated = saturatingAdd(addr);

        return updated >= BURST;
    }

    public void remove(InetAddress addr) {
        hitcounter.remove(addr);
    }


    public boolean test(InetAddress addr) {
        try {
            return hitcounter.getOrDefault(addr, 0) >= BURST;
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    int calculateDelayAndAdd(InetAddress addr) {
        int counter = hitcounter.compute(addr, (key, old) -> old == null ? 1 : old + 1);
        int diff = counter - BURST;
        return Math.max(diff, 0) * 1000 / PER_SECOND;
    }

    void saturatingDec(InetAddress addr) {
        hitcounter.compute(addr, (key, old) -> old == null || old == 1 ? null : old - 1);
    }

    private int saturatingAdd(InetAddress addr) {
        return hitcounter.compute(addr, (key, old) -> old == null ? 1 : Math.min(old + 1, BURST));
    }

    void decay() {
        long now = System.currentTimeMillis();
        long last = lastDecayTime.get();
        long deltaT = TimeUnit.MILLISECONDS.toSeconds(now - last);
        if (deltaT < 1)
            return;
        if (!lastDecayTime.compareAndSet(last, last + deltaT * 1000))
            return;

        int deltaC = (int) (deltaT * PER_SECOND);

        // minor optimization: delete first, then replace only what's left
        hitcounter.entrySet().removeIf(entry -> entry.getValue() <= deltaC);
        hitcounter.replaceAll((k, v) -> v - deltaC);

    }
}
