package io.ipfs.format;

import io.Closeable;
import io.ipfs.ClosedException;

public interface NavigableNode {

    NavigableNode FetchChild(Closeable ctx, int childIndex) throws ClosedException;

    int ChildTotal();

}
