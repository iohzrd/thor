package io.ipfs.format;

import androidx.annotation.NonNull;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;

public interface NavigableNode {

    NavigableNode fetchChild(@NonNull Closeable ctx, int childIndex) throws ClosedException;

    int childTotal();

    Cid getChild(int index);

    Cid getCid();
}
