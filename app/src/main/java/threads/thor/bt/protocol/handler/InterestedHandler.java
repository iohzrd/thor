package threads.thor.bt.protocol.handler;

import java.nio.ByteBuffer;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.Interested;

import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class InterestedHandler extends UniqueMessageHandler<Interested> {

    public InterestedHandler() {
        super(Interested.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Interested.class, 0, buffer.remaining());
        context.setMessage(Interested.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, Interested message, ByteBuffer buffer) {
        return true;
    }
}
