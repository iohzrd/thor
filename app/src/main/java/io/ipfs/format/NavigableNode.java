package io.ipfs.format;

import io.Closeable;

public interface NavigableNode {

    NavigableNode FetchChild(Closeable ctx, int childIndex);

    int ChildTotal();

}
