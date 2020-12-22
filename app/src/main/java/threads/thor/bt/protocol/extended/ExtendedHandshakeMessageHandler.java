package threads.thor.bt.protocol.extended;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.thor.bt.bencoding.BEInteger;
import threads.thor.bt.bencoding.BEMap;
import threads.thor.bt.bencoding.BEObject;
import threads.thor.bt.bencoding.BEParser;
import threads.thor.bt.bencoding.BEType;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.InvalidMessageException;
import threads.thor.bt.protocol.handler.MessageHandler;

class ExtendedHandshakeMessageHandler implements MessageHandler<ExtendedHandshake> {

    private final Collection<Class<? extends ExtendedHandshake>> supportedTypes;
    private final ConcurrentMap<Peer, Map<Integer, String>> peerTypeMappings;

    ExtendedHandshakeMessageHandler() {
        peerTypeMappings = new ConcurrentHashMap<>();
        supportedTypes = Collections.singleton(ExtendedHandshake.class);
    }

    @Override
    public Collection<Class<? extends ExtendedHandshake>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends ExtendedHandshake> readMessageType(ByteBufferView buffer) {
        return ExtendedHandshake.class;
    }

    private void processTypeMapping(Peer peer, BEObject mappingObj) {

        if (mappingObj == null) {
            return;
        }

        // TODO: use BE generic validation
        if (mappingObj.getType() != BEType.MAP) {
            throw new InvalidMessageException("Extended message types mapping must be a dictionary." +
                    " Actual BE type: " + mappingObj.getType().name());
        }

        @SuppressWarnings("unchecked")
        Map<String, BEObject> mapping = (Map<String, BEObject>) mappingObj.getValue();
        if (mapping.size() > 0) {
            // according to BEP-10, peers are only required to send a delta of changes
            // on subsequent handshakes, so we need to store all mappings received from the peer
            // and merge the changes..
            //
            // subsequent handshake messages can be used to enable/disable extensions
            // without restarting the connection
            peerTypeMappings.put(peer, mergeMappings(peerTypeMappings.getOrDefault(peer, new HashMap<>()), mapping));
        }
    }

    Map<Integer, String> getPeerTypeMapping(Peer peer) {
        Map<Integer, String> mapping = peerTypeMappings.get(peer);
        return (mapping == null) ? Collections.emptyMap() : Collections.unmodifiableMap(mapping);
    }

    private Map<Integer, String> mergeMappings(Map<Integer, String> existing, Map<String, BEObject> changes) {

        for (Map.Entry<String, BEObject> entry : changes.entrySet()) {
            String typeName = entry.getKey();
            int typeId = ((BEInteger) entry.getValue()).getValue().intValue();
            if (typeId == 0) {
                // by setting type ID to 0 peer signals that he has disabled this extension
                Iterator<Integer> iter = existing.keySet().iterator();
                while (iter.hasNext()) {
                    Integer key = iter.next();
                    if (typeName.equals(existing.get(key))) {
                        iter.remove();
                        break;
                    }
                }
            } else {
                existing.put(typeId, typeName);
            }
        }
        return existing;
    }

    @Override
    public int decode(DecodingContext context, ByteBufferView buffer) {

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap message = parser.readMap();

            Map<String, BEObject<?>> value = message.getValue();
            processTypeMapping(context.getPeer(), value.get(ExtendedHandshake.MESSAGE_TYPE_MAPPING_KEY));
            context.setMessage(new ExtendedHandshake(value));
            return message.getContent().length;
        } catch (Exception e) {
            // TODO: parser should be configurable to return null instead of throwing an exception;
            // otherwise need to treat exceptions differently depending on their type
            // (i.e. incorrect message vs incomplete message)
            throw new RuntimeException("Failed to decode extended handshake", e);
        }
    }

    @Override
    public boolean encode(EncodingContext context, ExtendedHandshake message, ByteBuffer buffer) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new BEMap(null, message.getData()).writeTo(out);
        } catch (IOException e) {
            // can't happen
        }

        byte[] payload = out.toByteArray();
        if (buffer.remaining() < payload.length) {
            return false;
        }

        buffer.put(payload);
        return true;
    }
}
