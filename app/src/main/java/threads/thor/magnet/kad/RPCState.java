package threads.thor.magnet.kad;

// TODO: split out termination reason from the case that the call terminated
public enum RPCState {
    UNSENT,
    SENT,
    STALLED,
    ERROR,
    TIMEOUT,
    RESPONDED
}
