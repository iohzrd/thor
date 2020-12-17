package threads.thor.bt.protocol.handler;

import java.nio.ByteBuffer;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.NotInterested;

import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class NotInterestedHandler extends UniqueMessageHandler<NotInterested> {

    public NotInterestedHandler() {
        super(NotInterested.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(NotInterested.class, 0, buffer.remaining());
        context.setMessage(NotInterested.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, NotInterested message, ByteBuffer buffer) {
        return true;
    }
}
