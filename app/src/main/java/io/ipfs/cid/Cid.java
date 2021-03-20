package io.ipfs.cid;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

public class Cid implements Comparable<Cid> {
    public static final String TAG = Cid.class.getSimpleName();
    public static final long IDENTITY = 0x00;
    public static final long Raw = 0x55;
    public static final long DagProtobuf = 0x70;
    public static final long DagCBOR = 0x71;
    public static final long Libp2pKey = 0x72;

    private final byte[] multihash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cid cid = (Cid) o;
        return Arrays.equals(multihash, cid.multihash);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(multihash);
    }

    public Cid(byte[] multihash) {
        this.multihash = multihash;
    }

    public static Cid Undef() {
        return new Cid(null);
    }

    public static Cid tryNewCidV0(byte[] mhash) {
        try {
            Multihash dec = Multihash.deserialize(mhash);

            if (dec.getType().index != Multihash.Type.sha2_256.index
                    || Multihash.Type.sha2_256.length != 32) {
                throw new RuntimeException("invalid hash for cidv0");
            }
            return new Cid(dec.toBytes());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static Cid Decode(@NonNull String v) {
        if (v.length() < 2) {
            throw new RuntimeException("invalid cid");
        }

        if (v.length() == 46 && v.startsWith("Qm")) {
            Multihash hash = Multihash.fromBase58(v);
            Objects.requireNonNull(hash);
            return new Cid(hash.toBytes());
        }

        byte[] data = Multibase.decode(v);

        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            long version = Multihash.readVarint(inputStream);
            if (version != 1) {
                throw new Exception("invalid version");
            }
            long codecType = Multihash.readVarint(inputStream);
            if (!(codecType == Cid.DagProtobuf || codecType == Cid.Raw || codecType == Cid.Libp2pKey)) {
                throw new Exception("not supported codec");
            }

            return new Cid(data);

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static Cid NewCidV0(byte[] mhash) {
        return tryNewCidV0(mhash);
    }

    public static Cid NewCidV1(long codecType, byte[] mhash) {

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Multihash.putUvarint(out, 1);
            Multihash.putUvarint(out, codecType);
            out.write(mhash);
            return new Cid(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void ValidateCid(Cid c) {
        /* TODO
        pref := c.Prefix();
        if !IsGoodHash(pref.MhType) {
            return ErrPossiblyInsecureHashFunction
        }

        if pref.MhType != mh.ID && pref.MhLength < minimumHashLength {
            return ErrBelowMinimumHashLength
        }

        return nil*/
    }

    public String String() {
        switch (Version()) {
            case 0:
                try {
                    return Multihash.deserialize(multihash).toBase58();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            case 1:
                return Multibase.encode(Multibase.Base.Base32, multihash);
            default:
                throw new RuntimeException();
        }
    }

    public int Version() {
        byte[] bytes = multihash;
        if (bytes.length == 34 && bytes[0] == 18 && bytes[1] == 32) {
            return 0;
        }
        return 1;
    }

    public long Type() {
        if (Version() == 0) {
            return DagProtobuf;
        }

        long type;
        try {
            InputStream is = new ByteArrayInputStream(multihash);
            Multihash.readVarint(is);
            type = Multihash.readVarint(is);
            is.close();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
        return type;
    }

    public byte[] Bytes() {
        return multihash;
    }

    public boolean Defined() {
        return multihash != null;
    }

    public Prefix Prefix() {

        if (Version() == 0) {
            return new Prefix(DagProtobuf, 32, Multihash.Type.sha2_256.index, 0);
        }
        try (InputStream inputStream = new ByteArrayInputStream(Bytes())) {
            long version = Multihash.readVarint(inputStream);
            if (version != 1) {
                throw new Exception("invalid version");
            }
            long codec = Multihash.readVarint(inputStream);
            if (!(codec == Cid.DagProtobuf || codec == Cid.Raw || codec == Cid.Libp2pKey)) {
                throw new Exception("not supported codec");
            }

            long mhtype = Multihash.readVarint(inputStream);

            long mhlen = Multihash.readVarint(inputStream);

            return new Prefix(codec, mhlen, mhtype, version);


        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }


    @Override
    public int compareTo(Cid o) {
        return Integer.compare(this.hashCode(), o.hashCode());
    }
}