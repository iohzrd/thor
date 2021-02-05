package threads.thor.magnet.kad.messages;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.TreeMap;

import threads.thor.magnet.kad.DHT;
import threads.thor.magnet.kad.DHT.DHTtype;
import threads.thor.magnet.kad.NodeList;

public class AbstractLookupResponse extends MessageBase {

    NodeList nodes;
    NodeList nodes6;
    private byte[] token;

    AbstractLookupResponse(byte[] mtid, Method m) {
        super(mtid, m, Type.RSP_MSG);
    }

    public void setNodes(NodeList nodes) {
        switch (nodes.type()) {
            case V4:
                this.nodes = nodes;
                break;

            case V6:
                this.nodes6 = nodes;
                break;
            default:
                throw new UnsupportedOperationException("should not happen");
        }
    }

    public byte[] getToken() {
        return token;
    }

    public void setToken(byte[] t) {
        token = t;
    }

    @Override
    public void apply(DHT dh_table) {
        dh_table.response(this);

    }

    @Override
    Map<String, Object> getInnerMap() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("id", id.getHash());
        if (token != null)
            inner.put("token", token);
        if (nodes != null)
            inner.put("nodes", nodes.writer());
        if (nodes6 != null)
            inner.put("nodes6", nodes6.writer());


        return inner;
    }

    public NodeList getNodes(DHTtype type) {
        if (type == DHTtype.IPV4_DHT)
            return nodes;
        if (type == DHTtype.IPV6_DHT)
            return nodes6;
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() +
                (nodes != null ? "contains: " + (nodes.packedSize() / DHTtype.IPV4_DHT.NODES_ENTRY_LENGTH) + " nodes " : "") +
                (nodes6 != null ? "contains: " + (nodes6.packedSize() / DHTtype.IPV6_DHT.NODES_ENTRY_LENGTH) + " nodes6 " : "") +
                (token != null ? "token " + token.length + " | " : "");
    }


}
