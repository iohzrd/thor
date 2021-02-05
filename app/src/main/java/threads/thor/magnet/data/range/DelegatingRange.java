package threads.thor.magnet.data.range;

/**
 * @since 1.3
 */
interface DelegatingRange<T extends Range<T>> {

    /**
     * @since 1.3
     */
    T getDelegate();
}
