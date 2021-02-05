package threads.thor.magnet.kad.messages;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import threads.thor.magnet.kad.DHT;
import threads.thor.magnet.kad.Key;

/**
 * @author Damokles
 */
public class AnnounceRequest extends AbstractLookupRequest {

    private final int port;
    private final byte[] token;
    private boolean isSeed;
    private ByteBuffer name;

    public AnnounceRequest(Key info_hash, int port, byte[] token) {
        super(info_hash, Method.ANNOUNCE_PEER);
        this.port = port;
        this.token = token;
    }

    public boolean isSeed() {
        return isSeed;
    }

    public void setSeed(boolean isSeed) {
        this.isSeed = isSeed;
    }

    @Override
    public void apply(DHT dh_table) {
        dh_table.announce(this);
    }


    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> inner = new TreeMap<>();

        inner.put("id", id.getHash());
        inner.put("info_hash", target.getHash());
        inner.put("port", port);
        inner.put("token", token);
        inner.put("seed", (long) (isSeed ? 1 : 0));
        if (name != null)
            inner.put("name", name);

        return inner;
    }

    public void setName(ByteBuffer name) {
        this.name = name;
    }

    private Optional<String> getNameUTF8() {
        return Optional.ofNullable(name).map(n -> StandardCharsets.UTF_8.decode(n.slice()).toString());
    }


    /**
     * @return the token
     */
    public byte[] getToken() {
        return token;
    }

    public int getPort() {
        return port;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " seed:" + isSeed + " token:" + token.length + " port:" + port + " name:" + getNameUTF8().orElse("");
    }

    @Override
    protected String targetBencodingName() {
        return "info_hash";
    }
}
