package threads.thor.bt.protocol.handler;

import java.nio.ByteBuffer;
import java.util.Objects;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.Cancel;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.InvalidMessageException;

import static threads.thor.bt.protocol.Protocols.readInt;
import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class CancelHandler extends UniqueMessageHandler<Cancel> {

    public CancelHandler() {
        super(Cancel.class);
    }

    // cancel: <len=0013><id=8><index><begin><length>
    private static boolean writeCancel(int pieceIndex, int offset, int length, ByteBuffer buffer) {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")");
        }
        if (buffer.remaining() < Integer.BYTES * 3) {
            return false;
        }

        buffer.putInt(pieceIndex);
        buffer.putInt(offset);
        buffer.putInt(length);

        return true;
    }

    private static int decodeCancel(DecodingContext context, ByteBufferView buffer) {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (buffer.remaining() >= length) {

            int pieceIndex = Objects.requireNonNull(readInt(buffer));
            int blockOffset = Objects.requireNonNull(readInt(buffer));
            int blockLength = Objects.requireNonNull(readInt(buffer));

            context.setMessage(new Cancel(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Cancel.class, 12, buffer.remaining());
        return decodeCancel(context, buffer);
    }

    @Override
    public boolean doEncode(EncodingContext context, Cancel message, ByteBuffer buffer) {
        return writeCancel(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
    }
}
