package threads.thor.bt.data;

import threads.thor.bt.data.range.Range;

public interface DataRange extends Range<DataRange> {

    void visitUnits(DataRangeVisitor visitor);

    DataRange getSubrange(long offset, long length);

    DataRange getSubrange(long offset);
}
