package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;

import threads.thor.bt.kad.tasks.Task;

/**
 * @author Damokles
 */
public class DHTStats {

    private static final double EMA_WEIGHT = 0.01;

    private DatabaseStats dbStats;

    private RPCStats rpcStats;

    private Instant startedTimestamp;

    /// number of peers in the routing table
    private int numPeers;
    /// Number of running tasks
    private int numTasks;

    private long numReceivedPackets;

    private long numSentPackets;

    private int numRpcCalls;

    private double avgFirstResultTime = 10000;
    private double avgFinishTime = 10000;

    /**
     * @return the num_peers
     */
    public int getNumPeers() {
        return numPeers;
    }

    /**
     * @param num_peers the num_peers to set
     */
    void setNumPeers(int num_peers) {
        this.numPeers = num_peers;
    }

    /**
     * @return the num_tasks
     */
    public int getNumTasks() {
        return numTasks;
    }

    /**
     * @param num_tasks the num_tasks to set
     */
    void setNumTasks(int num_tasks) {
        this.numTasks = num_tasks;
    }

    /**
     * @return the num_received_packets
     */
    public long getNumReceivedPackets() {
        return numReceivedPackets;
    }

    /**
     * @param num_received_packets the num_received_packets to set
     */
    void setNumReceivedPackets(long num_received_packets) {
        this.numReceivedPackets = num_received_packets;
    }

    /**
     * @return the num_sent_packets
     */
    public long getNumSentPackets() {
        return numSentPackets;
    }

    /**
     * @param num_sent_packets the num_sent_packets to set
     */
    void setNumSentPackets(long num_sent_packets) {
        this.numSentPackets = num_sent_packets;
    }

    /**
     * @return the numRpcCalls
     */
    public int getNumRpcCalls() {
        return numRpcCalls;
    }

    /**
     * @param numRpcCalls the numRpcCalls to set
     */
    void setNumRpcCalls(int numRpcCalls) {
        this.numRpcCalls = numRpcCalls;
    }

    /**
     * @return the dbStats
     */
    public DatabaseStats getDbStats() {
        return dbStats;
    }

    /**
     * @param dbStats the dbStats to set
     */
    void setDbStats(DatabaseStats dbStats) {
        this.dbStats = dbStats;
    }

    /**
     * @return the rpcStats
     */
    public RPCStats getRpcStats() {
        return rpcStats;
    }

    /**
     * @param rpcStats the rpcStats to set
     */
    void setRpcStats(RPCStats rpcStats) {
        this.rpcStats = rpcStats;
    }

    /**
     * @return the startedTimestamp
     */
    public Instant getStartedTimestamp() {
        return startedTimestamp;
    }

    public void taskFinished(Task t) {
        if (t.getFinishedTime() <= 0)
            return;
        avgFinishTime = (t.getFinishedTime() - t.getStartTime()) * EMA_WEIGHT + avgFinishTime * (1.0 - EMA_WEIGHT);
        //System.out.println("fin "+(t.getFinishedTime() - t.getStartTime()));
        if (t.getFirstResultTime() <= 0)
            return;
        avgFirstResultTime = (t.getFirstResultTime() - t.getStartTime()) * EMA_WEIGHT + avgFirstResultTime * (1.0 - EMA_WEIGHT);
        //System.out.println("1st "+(t.getFirstResultTime() - t.getStartTime()));
    }

    void resetStartedTimestamp() {
        startedTimestamp = Instant.now();
    }

    @NonNull
    @Override
    public String toString() {
        return "DB Keys: " + dbStats.getKeyCount() + '\n' +
                "DB Items: " + dbStats.getItemCount() + '\n' +
                "TX sum: " + numSentPackets + " RX sum: " + numReceivedPackets + '\n' +
                "avg task time/avg 1st result time (ms): " + (int) avgFinishTime + '/' + (int) avgFirstResultTime + '\n' +
                "Uptime: " + Duration.between(startedTimestamp, Instant.now()) + "s\n" +
                "RPC stats\n" +
                rpcStats.toString();
    }
}
