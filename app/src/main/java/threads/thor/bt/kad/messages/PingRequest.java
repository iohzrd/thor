package threads.thor.bt.kad.messages;

import java.util.Map;
import java.util.TreeMap;

import threads.thor.bt.kad.DHT;

/**
 * @author Damokles
 */
public class PingRequest extends MessageBase {

    public PingRequest() {
        super(null, Method.PING, Type.REQ_MSG);
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.messages.MessageBase#apply(threads.thor.bt.kad.DHT)
     */
    @Override
    public void apply(DHT dh_table) {
        dh_table.ping(this);
    }


    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("id", id.getHash());

        return inner;
    }
}
