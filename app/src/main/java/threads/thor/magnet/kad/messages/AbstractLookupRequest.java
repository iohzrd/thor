package threads.thor.magnet.kad.messages;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import threads.thor.magnet.kad.Key;

/**
 * @author Damokles
 */
public abstract class AbstractLookupRequest extends MessageBase {

    final Key target;
    private boolean want4;
    private boolean want6;


    AbstractLookupRequest(Key target, Method m) {
        super(null, m, Type.REQ_MSG);
        this.target = target;
    }

    @Override
    Map<String, Object> getInnerMap() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("id", id.getHash());
        inner.put(targetBencodingName(), target.getHash());
        List<String> want = new ArrayList<>(2);
        if (want4)
            want.add("n4");
        if (want6)
            want.add("n6");
        inner.put("want", want);

        return inner;

    }

    protected abstract String targetBencodingName();

    /**
     * @return the info_hash
     */
    public Key getTarget() {
        return target;
    }

    public boolean doesWant4() {
        return want4;
    }

    void decodeWant(List<byte[]> want) {
        if (want == null)
            return;

        List<String> wants = new ArrayList<>(2);
        for (byte[] bytes : want)
            wants.add(new String(bytes, StandardCharsets.ISO_8859_1));

        want4 |= wants.contains("n4");
        want6 |= wants.contains("n6");
    }

    public void setWant4(boolean want4) {
        this.want4 = want4;
    }

    public boolean doesWant6() {
        return want6;
    }

    public void setWant6(boolean want6) {
        this.want6 = want6;
    }

    @NonNull
    @Override
    public String toString() {
        //return super.toString() + "targetKey:"+target+" ("+(160-DHT.getSingleton().getOurID().findApproxKeyDistance(target))+")";
        return super.toString() + "targetKey:" + target;
    }

    /**
     * @return the info_hash
     */
    public Key getInfoHash() {
        return target;
    }
}
