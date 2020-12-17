package threads.thor.bt.protocol.handler;

import java.nio.ByteBuffer;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.Unchoke;

import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

    public UnchokeHandler() {
        super(Unchoke.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Unchoke.class, 0, buffer.remaining());
        context.setMessage(Unchoke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, Unchoke message, ByteBuffer buffer) {
        return true;
    }
}
