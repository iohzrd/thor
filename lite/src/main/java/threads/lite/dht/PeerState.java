package threads.lite.dht;

public enum PeerState {
    // PeerHeard is applied to peers which have not been queried yet.
    PeerHeard,
    // PeerWaiting is applied to peers that are currently being queried.
    PeerWaiting,
    // PeerQueried is applied to peers who have been queried and a response was retrieved successfully.
    PeerQueried,
    // PeerUnreachable is applied to peers who have been queried and a response was not retrieved successfully.
    PeerUnreachable
}
