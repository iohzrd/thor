package io.ipfs.datastore;

import androidx.annotation.NonNull;

public class Key {
    private String key;

    public Key(@NonNull String key) {
        this.key = key;
        clean();
    }

    public static Key RawKey(@NonNull String s) {

        if (s.length() == 0) {
            return new Key("/");
        }
        if (s.getBytes()[0] != '/') {
            throw new RuntimeException();
        }

        return new Key(s);
    }

    private void clean() {
        if (key.length() == 0) {
            key = "/";
        }
    }

    public String String() {
        return key;
    }
}
