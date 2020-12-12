package threads.thor.bt.kad.messages;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import threads.thor.bt.kad.Key;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class SampleResponse extends AbstractLookupResponse {

    private static final int MAX_INTERVAL = 6 * 3600;
    ByteBuffer samples;
    private int num;
    private int interval;

    public SampleResponse(byte[] mtid) {
        super(mtid, Method.SAMPLE_INFOHASHES, Type.RSP_MSG);
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int num() {
        return num;
    }

    public int interval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = max(0, min(interval, MAX_INTERVAL));
    }

    public boolean remoteSupportsSampling() {
        return samples != null;
    }

    public Collection<Key> getSamples() {
        if (samples == null || samples.remaining() == 0) {
            return Collections.emptyList();
        }

        List<Key> keys = new ArrayList<>();
        ByteBuffer copy = samples.duplicate();

        while (copy.hasRemaining()) {
            keys.add(new Key(copy));
        }

        return keys;
    }

    public void setSamples(ByteBuffer buf) {
        this.samples = buf;
    }

    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> inner = super.getInnerMap();

        inner.put("num", num);
        inner.put("interval", interval);
        inner.put("samples", samples);

        return inner;

    }

}
