package threads.thor.bt;

import androidx.annotation.NonNull;

import java.util.Random;

import threads.thor.bt.net.PeerId;

public class IdentityService {


    private final byte[] peerId;


    public IdentityService() {
        peerId = buildPeerId(buildVersionPrefix(new Version()));
    }


    private byte[] buildVersionPrefix(Version version) {

        int major = version.getMajor();
        if (major > Byte.MAX_VALUE) {
            throw new RuntimeException("Invalid major version: " + major);
        }

        int minor = version.getMinor();
        if (minor > Byte.MAX_VALUE) {
            throw new RuntimeException("Invalid major version: " + minor);
        }

        boolean snapshot = version.isSnapshot();
        return new byte[]{'-', 'B', 't', (byte) major, (byte) minor, 0, (byte) (snapshot ? 1 : 0), '-'};
    }

    private byte[] buildPeerId(byte[] versionPrefix) {

        if (versionPrefix.length >= PeerId.length()) {
            throw new IllegalArgumentException("Prefix is too long: " + versionPrefix.length);
        }

        byte[] tail = new byte[PeerId.length() - versionPrefix.length];
        Random random = new Random(System.currentTimeMillis());
        random.nextBytes(tail);

        byte[] peerId = new byte[PeerId.length()];
        System.arraycopy(versionPrefix, 0, peerId, 0, versionPrefix.length);
        System.arraycopy(tail, 0, peerId, versionPrefix.length, tail.length);
        return peerId;
    }

    public byte[] getID() {
        return peerId;
    }

    public static class Version {

        private final int major;
        private final int minor;
        private final boolean snapshot;

        Version() {
            this.major = 0;
            this.minor = 5;
            this.snapshot = false;
        }


        int getMajor() {
            return major;
        }

        int getMinor() {
            return minor;
        }

        boolean isSnapshot() {
            return snapshot;
        }

        @Override
        @NonNull
        public String toString() {
            String version = major + "." + minor;
            if (snapshot) {
                version += " (Snapshot)";
            }
            return version;
        }
    }

}
