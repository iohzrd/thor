package threads.thor.magnet.protocol.handler;

import java.nio.ByteBuffer;

import threads.thor.magnet.net.buffer.ByteBufferView;
import threads.thor.magnet.protocol.Choke;
import threads.thor.magnet.protocol.DecodingContext;
import threads.thor.magnet.protocol.EncodingContext;

import static threads.thor.magnet.protocol.Protocols.verifyPayloadHasLength;

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
