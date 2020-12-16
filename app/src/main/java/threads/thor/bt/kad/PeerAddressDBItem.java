package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import threads.thor.bt.bencode.Utils;
import threads.thor.bt.kad.DHT.DHTtype;

public class PeerAddressDBItem extends DBItem {


    boolean seed;
    private byte[] originatorVersion;

    public PeerAddressDBItem(byte[] data, boolean isSeed) {
        super(data);
        if (data.length != DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH && data.length != DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
            throw new IllegalArgumentException("byte array length does not match ipv4 or ipv6 raw InetAddress+Port length");
        seed = isSeed;
    }

    public static PeerAddressDBItem createFromAddress(InetAddress addr, int port, boolean isSeed) {
        byte[] tdata = new byte[addr.getAddress().length + 2];
        ByteBuffer bb = ByteBuffer.wrap(tdata);
        bb.put(addr.getAddress());
        bb.putShort((short) port);
        return new PeerAddressDBItem(tdata, isSeed);
    }

    public void setVersion(byte[] ary) {
        originatorVersion = ary;
    }

    public InetAddress getInetAddress() {
        try {
            if (item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
                return InetAddress.getByAddress(Arrays.copyOf(item, 4));
            if (item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
                return InetAddress.getByAddress(Arrays.copyOf(item, 16));
        } catch (UnknownHostException e) {
            // should not happen
            e.printStackTrace();
        }

        return null;
    }

    private InetSocketAddress toSocketAddress() {
        return new InetSocketAddress(getAddressAsString(), getPort());
    }

    private String getAddressAsString() {
        return getInetAddress().getHostAddress();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PeerAddressDBItem) {
            PeerAddressDBItem other = (PeerAddressDBItem) obj;
            if (other.item.length != item.length)
                return false;
            for (int i = 0; i < item.length - 2; i++)
                if (other.item[i] != item[i])
                    return false;
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(Arrays.copyOf(item, item.length - 2));
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(25);
        b.append(" addr:");
        b.append(toSocketAddress());
        b.append(" seed:");
        b.append(seed);
        if (originatorVersion != null)
            b.append(" version:").append(Utils.prettyPrint(originatorVersion));

        return b.toString();
    }

    public int getPort() {
        if (item.length == DHTtype.IPV4_DHT.ADDRESS_ENTRY_LENGTH)
            return (item[4] & 0xFF) << 8 | (item[5] & 0xFF);
        if (item.length == DHTtype.IPV6_DHT.ADDRESS_ENTRY_LENGTH)
            return (item[16] & 0xFF) << 8 | (item[17] & 0xFF);
        return 0;
    }

}
