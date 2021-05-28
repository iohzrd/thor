package io.ipfs.datastore;

import androidx.annotation.NonNull;

import com.google.common.io.BaseEncoding;

import io.ipfs.cid.Cid;

public class Dshelp {

    public static Key newKeyFromBinary(@NonNull byte[] rawKey) {
        return Key.getRawKey("/" +
                BaseEncoding.base32().encode(rawKey).replaceAll("=", ""));

    }

    public static Key cidToDsKey(@NonNull Cid cid) {
        return newKeyFromBinary(cid.bytes());
    }

}
