package io.ipfs.datastore;

import androidx.annotation.NonNull;

import com.google.common.io.BaseEncoding;

import io.ipfs.cid.Cid;

public class Dshelp {

    public static Key NewKeyFromBinary(@NonNull byte[] rawKey) {
        return Key.RawKey("/" +
                BaseEncoding.base32().encode(rawKey).replaceAll("=", ""));

    }

    public static Key CidToDsKey(@NonNull Cid cid) {
        return NewKeyFromBinary(cid.Bytes());
    }

}
