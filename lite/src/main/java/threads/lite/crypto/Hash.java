package threads.lite.crypto;

import org.bouncycastle.jcajce.provider.digest.SHA1;

@SuppressWarnings("unused")
public class Hash {

    public static byte[] sha1(byte[] data) {
        return (new SHA1.Digest()).digest(data);
    }


    public static byte[] sha256(byte[] data) {
        return (new org.bouncycastle.jcajce.provider.digest.SHA256.Digest()).digest(data);
    }


    public static byte[] sha512(byte[] data) {
        return (new org.bouncycastle.jcajce.provider.digest.SHA512.Digest()).digest(data);
    }
}
