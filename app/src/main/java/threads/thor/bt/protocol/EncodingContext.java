package threads.thor.bt.protocol;

import threads.thor.bt.net.Peer;

public class EncodingContext {

    private final Peer peer;

    public EncodingContext(Peer peer) {
        this.peer = peer;
    }

    public Peer getPeer() {
        return peer;
    }
}
