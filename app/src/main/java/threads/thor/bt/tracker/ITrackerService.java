
package threads.thor.bt.tracker;

import threads.thor.bt.BtException;

/**
 * This service acts a factory of trackers.
 *
 * @since 1.0
 */
public interface ITrackerService {

    /**
     * Check if the protocol specified in the tracker's URL is supported.
     *
     * @param trackerUrl Tracker URL
     * @return true if the protocol is supported
     * @since 1.1
     */
    boolean isSupportedProtocol(String trackerUrl);

    /**
     * Get a single tracker by its' URL
     *
     * @return Single tracker
     * @throws BtException if the protocol specified in the tracker's URL is not supported
     * @since 1.0
     */
    Tracker getTracker(String trackerUrl);

    /**
     * Get a tracker by its' announce key
     *
     * @return Either a single-tracker or a multi-tracker,
     * depending of the type of the announce key
     * @since 1.0
     */
    Tracker getTracker(AnnounceKey announceKey);
}
