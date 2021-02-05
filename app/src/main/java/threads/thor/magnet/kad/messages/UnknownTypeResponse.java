package threads.thor.magnet.kad.messages;

import threads.thor.magnet.kad.DHT;

/**
 * @author Damokles
 */
public class UnknownTypeResponse extends AbstractLookupResponse {
    public UnknownTypeResponse(byte[] mtid) {
        super(mtid, Method.UNKNOWN);
    }

    @Override
    public void apply(DHT dh_table) {
        throw new UnsupportedOperationException("incoming, unknown responses cannot be applied, they may only exist to send error messages");
    }
}
