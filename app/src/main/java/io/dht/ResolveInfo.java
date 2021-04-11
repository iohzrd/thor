package io.dht;

import io.core.Closeable;

public interface ResolveInfo extends Closeable {
    void resolved(byte[] data);
}
