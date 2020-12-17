package threads.thor.bt.protocol.extended;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import threads.thor.bt.protocol.handler.MessageHandler;

public class AlphaSortedMapping implements ExtendedMessageTypeMapping {

    private final Map<Integer, String> nameMap;
    private final Map<Class<?>, String> typeMap;

    /**
     * @since 1.0
     */
    public AlphaSortedMapping(Map<String, MessageHandler<? extends ExtendedMessage>> handlersByTypeName) {

        typeMap = new HashMap<>();

        TreeSet<String> sortedTypeNames = new TreeSet<>();
        handlersByTypeName.forEach((typeName, handler) -> {
            sortedTypeNames.add(typeName);
            handler.getSupportedTypes().forEach(messageType -> typeMap.put(messageType, typeName));
        });

        nameMap = new HashMap<>();

        Integer localTypeId = 1;
        for (String typeName : sortedTypeNames) {
            nameMap.put(localTypeId, typeName);
            localTypeId++;
        }
    }

    @Override
    public String getTypeNameForId(Integer typeId) {
        return nameMap.get(Objects.requireNonNull(typeId));
    }

    @Override
    public String getTypeNameForJavaType(Class<?> type) {
        return typeMap.get(Objects.requireNonNull(type));
    }

    @Override
    public void visitMappings(BiConsumer<String, Integer> visitor) {
        Objects.requireNonNull(visitor);
        nameMap.forEach((id, name) -> visitor.accept(name, id));
    }
}
