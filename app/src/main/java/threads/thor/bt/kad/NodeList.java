package threads.thor.bt.kad;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import threads.thor.bt.bencode.BEncoder.StringWriter;
import threads.thor.bt.kad.DHT.DHTtype;
import threads.thor.bt.kad.utils.AddressUtils;

import static threads.thor.bt.utils.Functional.unchecked;

public interface NodeList {

    static NodeList fromBuffer(ByteBuffer src, AddressType type) {
        Objects.requireNonNull(src);
        Objects.requireNonNull(type);

        return new NodeList() {

            @Override
            public int packedSize() {
                return src.remaining();
            }

            @Override
            public Stream<KBucketEntry> entries() {
                ByteBuffer buf = src.slice();

                byte[] rawId = new byte[20];
                byte[] rawAddr = new byte[type == AddressType.V4 ? 4 : 16];

                return IntStream.range(0, packedSize() / (type == AddressType.V4 ? DHTtype.IPV4_DHT.NODES_ENTRY_LENGTH : DHTtype.IPV6_DHT.NODES_ENTRY_LENGTH)).mapToObj(i -> {

                    buf.get(rawId);
                    buf.get(rawAddr);
                    int port = Short.toUnsignedInt(buf.getShort());

                    InetAddress addr = unchecked(() -> AddressUtils.fromBytesVerbatim(rawAddr));
                    Key id = new Key(rawId);

                    return new KBucketEntry(unchecked(() -> new InetSocketAddress(addr, port)), id);
                });
            }

            @Override
            public StringWriter writer() {
                return new StringWriter() {

                    @Override
                    public void writeTo(ByteBuffer buf) {
                        buf.put(src.slice());
                    }

                    @Override
                    public int length() {
                        return src.remaining();
                    }
                };
            }

            @Override
            public AddressType type() {
                return type;
            }
        };
    }

    AddressType type();

    Stream<KBucketEntry> entries();

    int packedSize();

    default StringWriter writer() {
        return new StringWriter() {

            @Override
            public void writeTo(ByteBuffer buf) {
                entries().forEach(e -> {

                    InetSocketAddress sockAddr = e.getAddress();
                    InetAddress addr = sockAddr.getAddress();
                    buf.put(e.getID().hash);
                    buf.put(addr.getAddress());
                    buf.putShort((short) sockAddr.getPort());
                });
            }

            @Override
            public int length() {
                return packedSize();
            }
        };
    }

    enum AddressType {
        V4, V6
    }

}
