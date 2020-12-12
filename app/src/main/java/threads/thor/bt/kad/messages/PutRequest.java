package threads.thor.bt.kad.messages;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import threads.thor.bt.bencode.BEncoder;
import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.GenericStorage;
import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.messages.ErrorMessage.ErrorCode;

import static threads.thor.bt.bencode.Utils.buf2ary;

public class PutRequest extends MessageBase {
	
	/*
{
    "a":
    {
        "cas": <optional expected seq-nr (int)>,
        "id": <20 byte id of sending node (string)>,
        "k": <ed25519 public key (32 bytes string)>,
        "salt": <optional salt to be appended to "k" when hashing (string)>
        "seq": <monotonically increasing sequence number (integer)>,
        "sig": <ed25519 signature (64 bytes string)>,
        "token": <write-token (string)>,
        "v": <any bencoded type, whose encoded size < 1000>
    },
    "t": <transaction-id (string)>,
    "y": "q",
    "q": "put"
}
	 */

    long expectedSequenceNumber = -1;
    long sequenceNumber = -1;
    byte[] pubkey;
    byte[] salt;
    byte[] signature;
    byte[] token;

    private byte[] value;


    PutRequest() {
        super(null, Method.PUT, Type.REQ_MSG);
    }


    @Override
    public Map<String, Object> getInnerMap() {
        Objects.requireNonNull(token);
        Objects.requireNonNull(value);
        Objects.requireNonNull(id);

        Map<String, Object> m = new TreeMap<>();

        if (expectedSequenceNumber != -1)
            m.put("cas", expectedSequenceNumber);
        if (sequenceNumber != -1)
            m.put("seq", sequenceNumber);
        if (salt != null)
            m.put("salt", salt);
        if (pubkey != null)
            m.put("k", pubkey);
        if (signature != null)
            m.put("sig", signature);

        m.put("token", token);
        m.put("v", new BEncoder.RawData(ByteBuffer.wrap(value)));
        m.put("id", id.getHash());


        return m;
    }


    @Override
    public void apply(DHT dh_table) {

        dh_table.put(this);
    }

    public void validate() throws MessageException {
        if (salt != null && salt.length > 64)
            throw new MessageException("salt too long", ErrorCode.SaltTooBig);
        if (token == null || value == null)
            throw new MessageException("required arguments for PUT request missing", ErrorCode.ProtocolError);
        if (value.length > 1000)
            throw new MessageException("bencoded PUT value ('v') field exceeds 1000 bytes", ErrorCode.PutMessageTooBig);
        if ((pubkey != null || salt != null || signature != null || expectedSequenceNumber >= 0 || sequenceNumber >= 0) && (pubkey == null || signature == null))
            throw new MessageException("PUT request contained at least one field indicating mutable data but other fields mandatory for mutable PUTs were missing", ErrorCode.ProtocolError);
    }

    public byte[] getToken() {
        return token;
    }

    public void setToken(byte[] token) {
        this.token = token;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public byte[] getPubkey() {
        return pubkey;
    }

    public byte[] getSalt() {
        return salt;
    }


    public byte[] getSignature() {
        return signature;
    }

    private void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public Key deriveTargetKey() {
        return GenericStorage.fingerprint(pubkey, salt, getValue());
    }

    public long getExpectedSequenceNumber() {
        return expectedSequenceNumber;
    }

    public void setExpectedSequenceNumber(long expectedSequenceNumber) {
        this.expectedSequenceNumber = expectedSequenceNumber;
    }

    public ByteBuffer getValue() {
        return ByteBuffer.wrap(value);
    }

    public void setValue(ByteBuffer value) {
        this.value = buf2ary(value);
    }

}
