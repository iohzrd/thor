package threads.thor.magnet.protocol.extended;

import threads.thor.magnet.protocol.Message;

public class ExtendedMessage implements Message {

    @Override
    public Integer getMessageId() {
        return ExtendedProtocol.EXTENDED_MESSAGE_ID;
    }
}
