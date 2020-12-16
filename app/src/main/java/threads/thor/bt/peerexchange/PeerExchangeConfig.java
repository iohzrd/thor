package threads.thor.bt.peerexchange;

import java.time.Duration;

public class PeerExchangeConfig {

    private final Duration minMessageInterval;
    private final Duration maxMessageInterval;
    private final int minEventsPerMessage;
    private final int maxEventsPerMessage;

    public PeerExchangeConfig() {
        this.minMessageInterval = Duration.ofMinutes(1);
        this.maxMessageInterval = Duration.ofMinutes(5);
        this.minEventsPerMessage = 10;
        this.maxEventsPerMessage = 50;
    }

    /**
     * @since 1.0
     */
    public Duration getMinMessageInterval() {
        return minMessageInterval;
    }

    /**
     * @since 1.0
     */
    public int getMinEventsPerMessage() {
        return minEventsPerMessage;
    }

    /**
     * @since 1.0
     */
    public int getMaxEventsPerMessage() {
        return maxEventsPerMessage;
    }

    /**
     * @since 1.9
     */
    public Duration getMaxMessageInterval() {
        return maxMessageInterval;
    }

}
