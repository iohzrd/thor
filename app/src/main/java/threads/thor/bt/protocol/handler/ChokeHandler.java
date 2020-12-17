package threads.thor.bt.protocol.handler;

import java.nio.ByteBuffer;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.Choke;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;

import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class ChokeHandler extends UniqueMessageHandler<Choke> {

    public ChokeHandler() {
        super(Choke.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Choke.class, 0, buffer.remaining());
        context.setMessage(Choke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, Choke message, ByteBuffer buffer) {
        return true;
    }
}
