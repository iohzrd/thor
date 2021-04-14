package io.dht;

import io.core.Closeable;

public interface RecordReportFunc {
     boolean func(Closeable ctx, RecordInfo v, boolean better);
}

