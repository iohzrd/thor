package io.dht;

public class Quorum extends Option {
    private final int quorum;

    public Quorum(int quorum) {
        this.quorum = quorum;
    }

    public int getQuorum() {
        return quorum;
    }
}
