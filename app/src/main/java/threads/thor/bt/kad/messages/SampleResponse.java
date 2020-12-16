package threads.thor.bt.kad.messages;

import java.nio.ByteBuffer;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class SampleResponse extends AbstractLookupResponse {

    private static final int MAX_INTERVAL = 6 * 3600;
    ByteBuffer samples;
    private int num;
    private int interval;

    public SampleResponse(byte[] mtid) {
        super(mtid, Method.SAMPLE_INFOHASHES);
    }

    public void setNum(int num) {
        this.num = num;
    }

    public void setInterval(int interval) {
        this.interval = max(0, min(interval, MAX_INTERVAL));
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
