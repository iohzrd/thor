package threads.thor.magnet.kad.messages;

import threads.thor.magnet.kad.DHT;
import threads.thor.magnet.kad.Key;

public class UnknownTypeRequest extends AbstractLookupRequest {

    public UnknownTypeRequest(Key target) {
        super(target, Method.UNKNOWN);
    }

    @Override
    protected String targetBencodingName() {
        throw new UnsupportedOperationException("the name is only used for encoding. encoding of unknown requests is not supported");
    }

    @Override
    public void apply(DHT dh_table) {
        dh_table.findNode(this);
    }

}
