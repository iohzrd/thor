
package threads.thor.bt.data;

import threads.thor.bt.data.range.Range;

public interface DataRange extends Range<DataRange> {

    /**
     * Traverse the storage units in this data range.
     *
     * @since 1.2
     */
    void visitUnits(DataRangeVisitor visitor);

    /**
     * {@inheritDoc}
     *
     * @since 1.3
     */
    DataRange getSubrange(long offset, long length);

    /**
     * {@inheritDoc}
     *
     * @since 1.3
     */
    DataRange getSubrange(long offset);
}
