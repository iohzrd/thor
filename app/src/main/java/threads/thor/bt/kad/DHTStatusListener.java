package threads.thor.bt.kad;

/**
 * @author Damokles
 */
interface DHTStatusListener {
    void statusChanged(DHTStatus newStatus, DHTStatus oldStatus);
}
