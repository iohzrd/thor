package io.ipfs.format;

import androidx.annotation.NonNull;

import io.Closeable;
import io.ipfs.ClosedException;
import io.ipfs.cid.Cid;

public interface NavigableNode {

    NavigableNode FetchChild(@NonNull Closeable ctx, int childIndex) throws ClosedException;

    int ChildTotal();

    Cid getChild(int index);

    Cid Cid();
}
