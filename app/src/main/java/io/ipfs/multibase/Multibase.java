package io.ipfs.multibase;

import java.util.Map;
import java.util.TreeMap;

import io.ipfs.multibase.binary.Base32;

public class Multibase {

    public static String encode(Base b, byte[] data) {
        switch (b) {
            case Base58BTC:
                return b.prefix + Base58.encode(data);
            case Base16:
                return b.prefix + Base16.encode(data);
            case Base32:
                return b.prefix + new String(new Base32().encode(data)).toLowerCase().replaceAll("=", "");
            case Base32Upper:
                return b.prefix + new String(new Base32().encode(data)).replaceAll("=", "");
            case Base32Hex:
                return b.prefix + new String(new Base32(true).encode(data)).toLowerCase().replaceAll("=", "");
            case Base32HexUpper:
                return b.prefix + new String(new Base32(true).encode(data)).replaceAll("=", "");
            case Base36:
                return b.prefix + Base36.encode(data);
            default:
                throw new IllegalStateException("Unsupported base encoding: " + b.name());
        }
    }

    public static Base encoding(String data) {
        return Base.lookup(data.charAt(0));
    }

    public static byte[] decode(String data) {
        Base b = encoding(data);
        String rest = data.substring(1);
        switch (b) {
            case Base58BTC:
                return Base58.decode(rest);
            case Base16:
                return Base16.decode(rest);
            case Base32:
                return new Base32().decode(rest);
            case Base32Upper:
                return new Base32().decode(rest.toLowerCase());
            case Base32Hex:
                return new Base32(true).decode(rest);
            case Base32HexUpper:
                return new Base32(true).decode(rest.toLowerCase());
            case Base36:
                return Base36.decode(rest);
            default:
                throw new IllegalStateException("Unsupported base encoding: " + b.name());
        }
    }

    public enum Base {
        Base1('1'),
        Base2('0'),
        Base8('7'),
        Base10('9'),
        Base16('f'),
        Base32('b'),
        Base32Upper('B'),
        Base32Hex('v'),
        Base32HexUpper('V'),
        Base36('k'),
        Base58Flickr('Z'),
        Base58BTC('z');

        private static final Map<Character, Base> lookup = new TreeMap<>();

        static {
            for (Base b : Base.values())
                lookup.put(b.prefix, b);
        }

        public char prefix;

        Base(char prefix) {
            this.prefix = prefix;
        }

        public static Base lookup(char p) {
            if (!lookup.containsKey(p))
                throw new IllegalStateException("Unknown Multibase type: " + p);
            return lookup.get(p);
        }
    }
}
