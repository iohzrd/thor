package threads.thor.magnet.protocol.handler;

import java.nio.ByteBuffer;

import threads.thor.magnet.net.buffer.ByteBufferView;
import threads.thor.magnet.protocol.Bitfield;
import threads.thor.magnet.protocol.DecodingContext;
import threads.thor.magnet.protocol.EncodingContext;

public final class BitfieldHandler extends UniqueMessageHandler<Bitfield> {

    public BitfieldHandler() {
        super(Bitfield.class);
    }

    // bitfield: <len=0001+X><id=5><bitfield>
    private static int decodeBitfield(DecodingContext context, ByteBufferView buffer, int length) {

        int consumed = 0;

        if (buffer.remaining() >= length) {
            byte[] bitfield = new byte[length];
            buffer.get(bitfield);
            context.setMessage(new Bitfield(bitfield));
            consumed = length;
        }

        return consumed;
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        return decodeBitfield(context, buffer, buffer.remaining());
    }

    @Override
    public boolean doEncode(EncodingContext context, Bitfield message, ByteBuffer buffer) {
        if (buffer.remaining() < message.getBitfield().length) {
            return false;
        }
        buffer.put(message.getBitfield());
        return true;
    }
}
