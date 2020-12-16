package threads.thor.bt.kad.messages;

import androidx.annotation.NonNull;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import threads.thor.bt.bencode.BEncoder;
import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.DHTConstants;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.RPCCall;
import threads.thor.bt.kad.RPCServer;
import threads.thor.bt.kad.utils.AddressUtils;

import static threads.thor.bt.bencode.Utils.prettyPrint;

/**
 * Base class for all RPC messages.
 *
 * @author Damokles
 */
public abstract class MessageBase {

    public static final String VERSION_KEY = "v";
    public static final String TRANSACTION_KEY = "t";
    public static final Map<String, Method> messageMethod = Arrays.stream(Method.values()).filter(e -> e != Method.UNKNOWN).collect(Collectors.toMap(Method::getRPCName, Function.identity()));
    static final String EXTERNAL_IP_KEY = "ip";
    private final Type type;
    Method method;
    Key id;
    private byte[] mtid;
    // TODO: unify as remoteAddress
    private InetSocketAddress origin;
    private InetSocketAddress destination;
    // for outgoing messages this is the IP we tell them
    // for incoming messages this is the IP they told us
    private InetSocketAddress publicIP;
    private byte[] version;
    private RPCServer srv;
    private RPCCall associatedCall;

    MessageBase(byte[] mtid, Method m, Type type) {
        this.mtid = mtid;
        this.method = m;
        this.type = type;
    }

    /**
     * When this message arrives this function will be called upon the DHT.
     * The message should then call the appropriate DHT function (double dispatch)
     *
     * @param dh_table Pointer to DHT
     */
    public abstract void apply(DHT dh_table);


    public void encode(ByteBuffer target) {
        new BEncoder().encodeInto(getBase(), target);
    }

    public Map<String, Object> getBase() {
        Map<String, Object> base = new TreeMap<>();
        Map<String, Object> inner = getInnerMap();
        if (inner != null)
            base.put(getType().innerKey(), inner);

        assert (mtid != null);
        // transaction ID
        base.put(TRANSACTION_KEY, mtid);
        // version
        base.put(VERSION_KEY, DHTConstants.getVersion());


        // message type
        base.put(Type.TYPE_KEY, getType().getRPCTypeName());
        // message method if we're a request
        if (getType() == Type.REQ_MSG)
            base.put(getType().getRPCTypeName(), getMethod().getRPCName());
        if (publicIP != null && getType() == Type.RSP_MSG)
            base.put(EXTERNAL_IP_KEY, AddressUtils.packAddress(publicIP));

        return base;
    }

    Map<String, Object> getInnerMap() {
        return null;
    }

    public InetSocketAddress getOrigin() {
        return origin;
    }

    public void setOrigin(InetSocketAddress o) {
        origin = o;
    }

    /// Get the origin
    public InetSocketAddress getDestination() {
        return destination;
    }

    // where the message was sent to
    public void setDestination(InetSocketAddress o) {
        destination = o;
    }

    /// Get the MTID
    public byte[] getMTID() {
        return mtid;
    }

    /// Set the MTID
    public void setMTID(byte[] m) {
        mtid = m;
    }

    public InetSocketAddress getPublicIP() {
        return publicIP;
    }

    public void setPublicIP(InetSocketAddress publicIP) {
        this.publicIP = publicIP;
    }

    public Optional<byte[]> getVersion() {
        return Optional.ofNullable(version).map(byte[]::clone);
    }

    public void setVersion(byte[] version) {
        this.version = version;
    }

    public RPCServer getServer() {
        return srv;
    }

    public void setServer(RPCServer srv) {
        this.srv = srv;
    }

    /// Get the id of the sender
    public Key getID() {
        return id;
    }

    public void setID(Key id) {
        this.id = id;
    }

    /**
     * only incoming replies have an associated call. the relation of outgoing request to call is tracked inside the call
     * <p>
     * TODO: determine if that can be changed
     */
    public RPCCall getAssociatedCall() {
        return associatedCall;
    }

    public void setAssociatedCall(RPCCall associatedCall) {
        this.associatedCall = associatedCall;
    }

    /// Get the type of the message
    public Type getType() {
        return type;
    }

    /// Get the message it's method
    public Method getMethod() {
        return method;
    }

    @NonNull
    @Override
    public String toString() {
        return " Method:" + method + " Type:" + type + " MessageID:" + (mtid != null ? prettyPrint(mtid) : null) + (version != null ? " version:" + prettyPrint(version) : "") + "  ";
    }

    public enum Type {
        REQ_MSG {
            @Override
            String innerKey() {
                return "a";
            }

            @Override
            String getRPCTypeName() {
                return "q";
            }
        }, RSP_MSG {
            @Override
            String innerKey() {
                return "r";
            }

            @Override
            String getRPCTypeName() {
                return "r";
            }
        }, ERR_MSG {
            @Override
            String getRPCTypeName() {
                return "e";
            }

            @Override
            String innerKey() {
                return "e";
            }
        };

        public static final String TYPE_KEY = "y";

        String innerKey() {
            return null;
        }

        String getRPCTypeName() {
            return null;
        }
    }

    public enum Method {
        PING, FIND_NODE, GET_PEERS, ANNOUNCE_PEER, GET, PUT, SAMPLE_INFOHASHES, UNKNOWN;

        String getRPCName() {
            return name().toLowerCase();
        }
    }

}
