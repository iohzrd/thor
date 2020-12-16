package threads.thor.bt.processor.listener;

public enum ProcessingEvent {

    /**
     * Torrent metadata has been fetched.
     *
     * @since 1.5
     */
    TORRENT_FETCHED,

    /**
     * Files to download have been chosen.
     *
     * @since 1.7
     */
    FILES_CHOSEN,

    /**
     * All data has been downloaded.
     *
     * @since 1.5
     */
    DOWNLOAD_COMPLETE
}
