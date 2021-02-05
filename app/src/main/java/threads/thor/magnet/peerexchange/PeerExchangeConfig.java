package threads.thor.magnet.peerexchange;

import java.time.Duration;

public class PeerExchangeConfig {

    private final Duration minMessageInterval;
    private final Duration maxMessageInterval;

    public PeerExchangeConfig() {
        this.minMessageInterval = Duration.ofMinutes(1);
        this.maxMessageInterval = Duration.ofMinutes(5);
    }


    public Duration getMinMessageInterval() {
        return minMessageInterval;
    }


    public Duration getMaxMessageInterval() {
        return maxMessageInterval;
    }

}
