package threads.thor.magnet.kad.messages;

import java.util.Map;
import java.util.TreeMap;

import threads.thor.magnet.kad.DHT;

/**
 * @author Damokles
 */
public class AnnounceResponse extends MessageBase {


    public AnnounceResponse(byte[] mtid) {
        super(mtid, Method.ANNOUNCE_PEER, Type.RSP_MSG);
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.messages.MessageBase#apply(threads.thor.bt.kad.DHT)
     */
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
