package threads.thor.bt.kad.messages;

import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.Key;

public class SampleRequest extends AbstractLookupRequest {

    public SampleRequest(Key target) {
        super(target, Method.SAMPLE_INFOHASHES);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected String targetBencodingName() {
        return "target";
    }

    @Override
    public void apply(DHT dh_table) {
        dh_table.sample(this);
    }

}
