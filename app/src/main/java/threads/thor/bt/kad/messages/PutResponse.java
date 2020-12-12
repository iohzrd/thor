package threads.thor.bt.kad.messages;

import java.util.Map;
import java.util.TreeMap;

import threads.thor.bt.kad.DHT;

public class PutResponse extends MessageBase {

    public PutResponse(byte[] mtid) {
        super(mtid, Method.PUT, Type.RSP_MSG);
    }

    @Override
    public void apply(DHT dh_table) {
        dh_table.response(this);
    }

    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("id", id.getHash());

        return inner;
    }

}
