package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;

public interface NavigableNode {

    NavigableNode FetchChild(@NonNull Closeable ctx, int childIndex) throws ClosedException;

    int ChildTotal();

    Cid getChild(int index);

    Cid Cid();
}
