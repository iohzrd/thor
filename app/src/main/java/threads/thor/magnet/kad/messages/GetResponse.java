package threads.thor.magnet.kad.messages;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Map;

import threads.thor.magnet.bencode.BEncoder;

public class GetResponse extends AbstractLookupResponse {

    private ByteBuffer rawValue;
    private byte[] signature;

    private long sequenceNumber = -1;
    private byte[] key;

    public GetResponse(byte[] mtid) {
        super(mtid, Method.GET);
    }

    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> map = super.getInnerMap();

        if (signature != null)
            map.put("sig", signature);
        if (key != null)
            map.put("k", key);
        if (sequenceNumber > -1)
            map.put("seq", sequenceNumber);

        if (rawValue != null)
            map.put("v", new BEncoder.RawData(rawValue));

        return map;

    }


    public void setRawValue(ByteBuffer rawValue) {
        this.rawValue = rawValue;
    }


    public void setSignature(byte[] signature) {
        this.signature = signature;
    }


    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() +
                (rawValue != null ? "rawval: " + rawValue.remaining() + "bytes " : "") +
                (signature != null ? "sig: " + signature.length + "bytes " : "") +
                (sequenceNumber != -1 ? "seq: " + sequenceNumber + " " : "") +
                (key != null ? "key: " + key.length + "bytes " : "")
                ;
    }

}
