package io.dht;

import io.core.Closeable;

public interface RecordReportFunc {
     boolean func(Closeable ctx, RecordVal v, boolean better);
}

