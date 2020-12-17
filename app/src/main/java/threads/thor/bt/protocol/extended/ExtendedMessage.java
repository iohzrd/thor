package threads.thor.bt.protocol.extended;

import threads.thor.bt.protocol.Message;

public class ExtendedMessage implements Message {

    @Override
    public Integer getMessageId() {
        return ExtendedProtocol.EXTENDED_MESSAGE_ID;
    }
}
