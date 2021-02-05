package threads.thor.magnet.kad.messages;

import java.util.Map;

import threads.thor.magnet.kad.DHT;
import threads.thor.magnet.kad.Key;

public class GetRequest extends AbstractLookupRequest {

    private long onlySendValueIfSeqGreaterThan = -1;

    public GetRequest(Key target) {
        super(target, Method.GET);
        // TODO Auto-generated constructor stub
    }


    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> m = super.getInnerMap();

        if (onlySendValueIfSeqGreaterThan != -1)
            m.put("seq", onlySendValueIfSeqGreaterThan);

        return m;
    }

    @Override
    protected String targetBencodingName() {
        return "target";
    }

    public long getSeq() {
        return onlySendValueIfSeqGreaterThan;
    }

    public void setSeq(long l) {
        onlySendValueIfSeqGreaterThan = l;
    }

    @Override
    public void apply(DHT dh_table) {
        dh_table.get(this);
    }

}
