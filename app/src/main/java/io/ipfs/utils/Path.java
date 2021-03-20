package io.ipfs.utils;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.ipfs.cid.Cid;

public class Path {
    private final String path;

    public Path(@NonNull String path) {
        this.path = path;
    }

    public static Path New(@NonNull String p) {
        return ParsePath(p);
    }


    public static Path ParseCidToPath(String txt) {
        if (txt.isEmpty()) {
            throw new RuntimeException("path is empty");
        }

        Cid c = Cid.Decode(txt);

        return FromCid(c);
    }


    public static Pair<Cid, List<String>> SplitAbsPath(Path fpath) {
        List<String> parts = fpath.Segments();
        String ident = parts.get(0);
        if (Objects.equals(ident, "ipfs") || (Objects.equals(ident, "ipld"))) {
            parts.remove(ident);
        }


        // if nothing, bail.
        if (parts.size() == 0) {
            throw new RuntimeException();
        }

        String txt = parts.get(0);
        Cid cid = decodeCid(txt);
        parts.remove(txt);

        return Pair.create(cid, parts);
    }


    // FromCid safely converts a cid.Cid type to a Path type.
    public static Path FromCid(@NonNull Cid cid) {
        return new Path("/ipfs/" + cid.String());
    }

    public static Path ParsePath(@NonNull String txt) {
        String[] parts = txt.split("/");

        if (parts.length == 1) {
            return ParseCidToPath(txt);
        }

        // if the path doesnt begin with a '/'
        // we expect this to start with a hash, and be an 'ipfs' path
        if (!parts[0].equals("")) {
            decodeCid(parts[0]); // throws exception
            // The case when the path starts with hash without a protocol prefix
            return new Path("/ipfs/" + txt);
        }

        if (parts.length < 3) {
            throw new RuntimeException("path does not begin with '/'");
        }

        //TODO: make this smarter
        String ident = parts[1];
        if (Objects.equals(ident, "ipfs") || Objects.equals(ident, "ipld")) {
            if (Objects.equals(parts[2], "")) {
                throw new RuntimeException("not enough path components");
            }
            decodeCid(parts[2]);
        } else if (Objects.equals(ident, "ipns")) {
            if (Objects.equals(parts[2], "")) {
                throw new RuntimeException("not enough path components");
            }
        }
        return new Path(txt);
    }


    public static Cid decodeCid(@NonNull String cstr) {
        Cid c = Cid.Decode(cstr);

        if (cstr.length() == 46 && cstr.startsWith("qm")) { // https://github.com/ipfs/go-ipfs/issues/7792
            throw new RuntimeException("(possible lowercased CIDv0; consider converting to a case-agnostic CIDv1, such as base32)");
        }
        return c;
    }

    public List<String> Segments() {
        String[] spits = path.split("/");
        List<String> result = new ArrayList<>();
        for (String split : spits) {
            if (!split.isEmpty()) {
                result.add(split);
            }
        }
        return result;
    }

    public String String() {
        return path;
    }
}