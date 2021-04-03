package io.dht;

import io.Closeable;

public interface ResolveInfo extends Closeable {
     void resolved(byte[] data);
}
