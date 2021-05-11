package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;
import io.core.Closeable;
import io.core.ClosedException;

public interface NavigableNode {

    NavigableNode FetchChild(@NonNull Closeable ctx, int childIndex) throws ClosedException;

    int ChildTotal();

    Cid getChild(int index);

    Cid Cid();
}
