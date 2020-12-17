package threads.thor.bt.protocol;

import threads.thor.bt.protocol.handler.PortMessageHandler;

public final class Port implements Message {

    private final int port;


    public Port(int port) throws InvalidMessageException {

        if (port < 0 || port > 65535) {
            throw new InvalidMessageException("Invalid argument: port (" + port + ")");
        }

        this.port = port;
    }

    /**
     * @since 1.1
     */
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] port {" + port + "}";
    }

    @Override
    public Integer getMessageId() {
        return PortMessageHandler.PORT_ID;
    }
}
