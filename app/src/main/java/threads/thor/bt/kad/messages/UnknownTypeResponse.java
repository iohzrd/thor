package threads.thor.bt.kad.messages;

import threads.thor.bt.kad.DHT;

/**
 * @author Damokles
 */
public class UnknownTypeResponse extends AbstractLookupResponse {
    public UnknownTypeResponse(byte[] mtid) {
        super(mtid, Method.UNKNOWN, Type.RSP_MSG);
    }

    @Override
    public void apply(DHT dh_table) {
        throw new UnsupportedOperationException("incoming, unknown responses cannot be applied, they may only exist to send error messages");
    }
}
