package io.libp2p.protocol;

import androidx.annotation.NonNull;

import java.util.Objects;


public class Protocol {
    public static final Protocol ProtocolLite = new Protocol("/ipfs/lite/1.0.0");
    public static final Protocol ProtocolBitswap = new Protocol("/ipfs/bitswap/1.2.0");
    public static final Protocol ProtocolBitswapOneOne = new Protocol("/ipfs/bitswap/1.1.0");
    private final String id;

    public Protocol(@NonNull String id) {
        this.id = id;
    }

    public static Protocol create(@NonNull String protocol) {

        if (Objects.equals(protocol, ProtocolLite.id)) {
            return ProtocolLite;
        } else if (Objects.equals(protocol, ProtocolBitswap.id)) {
            return ProtocolBitswap;
        } else if (Objects.equals(protocol, ProtocolBitswapOneOne.id)) {
            return ProtocolBitswapOneOne;
        } else {
            throw new RuntimeException();
        }
    }

    public String String() {
        return id;
    }
}
