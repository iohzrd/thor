/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.bencoding;

import threads.thor.bt.bencoding.model.BEInteger;
import threads.thor.bt.bencoding.model.BEList;
import threads.thor.bt.bencoding.model.BEMap;
import threads.thor.bt.bencoding.model.BEObject;
import threads.thor.bt.bencoding.model.BEString;

/**
 * BEncoding parser. Should be closed when the source is processed.
 *
 * @since 1.0
 */
public class BEParser implements AutoCloseable {

    static final char EOF = 'e';

    static final char INTEGER_PREFIX = 'i';
    static final char LIST_PREFIX = 'l';
    static final char MAP_PREFIX = 'd';
    private final BEType type;
    private final Scanner scanner;
    private Object parsedObject;

    /**
     * Create a parser for the provided bencoded document.
     *
     * @param bs Bencoded document.
     * @since 1.0
     */
    public BEParser(byte[] bs) {

        if (bs == null || bs.length == 0) {
            throw new IllegalArgumentException("Can't parse bytes array: null or empty");
        }
        this.scanner = new Scanner(bs);
        this.type = getTypeForPrefix((char) scanner.peek());
    }

    static char getPrefixForType(BEType type) {

        if (type == null) {
            throw new NullPointerException("Can't get prefix -- type is null");
        }

        switch (type) {
            case INTEGER:
                return INTEGER_PREFIX;
            case LIST:
                return LIST_PREFIX;
            case MAP:
                return MAP_PREFIX;
            default:
                throw new IllegalArgumentException("Unknown type: " + type.name().toLowerCase());
        }
    }

    static BEType getTypeForPrefix(char c) {
        if (Character.isDigit(c)) {
            return BEType.STRING;
        }
        switch (c) {
            case INTEGER_PREFIX: {
                return BEType.INTEGER;
            }
            case LIST_PREFIX: {
                return BEType.LIST;
            }
            case MAP_PREFIX: {
                return BEType.MAP;
            }
            default: {
                throw new IllegalStateException("Invalid type prefix: " + c);
            }
        }
    }

    static BEObjectBuilder<? extends BEObject<?>> builderForType(BEType type) {
        switch (type) {
            case STRING: {
                return new BEStringBuilder();
            }
            case INTEGER: {
                return new BEIntegerBuilder();
            }
            case LIST: {
                return new BEListBuilder();
            }
            case MAP: {
                return new BEMapBuilder();
            }
            default: {
                throw new IllegalStateException("Unknown type: " + type.name().toLowerCase());
            }
        }
    }

    /**
     * Read type of the root object of the bencoded document that this parser was created for.
     *
     * @since 1.0
     */
    public BEType readType() {
        return type;
    }

    /**
     * Try to read the document's root object as a bencoded string.
     *
     * @see #readType()
     * @since 1.0
     */
    public BEString readString() {
        return readObject(BEType.STRING, BEStringBuilder.class);
    }

    /**
     * Try to read the document's root object as a bencoded integer.
     *
     * @see #readType()
     * @since 1.0
     */
    public BEInteger readInteger() {
        return readObject(BEType.INTEGER, BEIntegerBuilder.class);
    }

    /**
     * Try to read the document's root object as a bencoded list.
     *
     * @see #readType()
     * @since 1.0
     */
    public BEList readList() {
        return readObject(BEType.LIST, BEListBuilder.class);
    }

    /**
     * Try to read the document's root object as a bencoded dictionary.
     *
     * @see #readType()
     * @since 1.0
     */
    public BEMap readMap() {
        return readObject(BEType.MAP, BEMapBuilder.class);
    }

    private <T extends BEObject> T readObject(BEType type, Class<? extends BEObjectBuilder<T>> builderClass) {

        assertType(type);

        @SuppressWarnings("unchecked")
        T result = (T) parsedObject;
        if (result == null) {
            try {
                // relying on the default constructor being present
                parsedObject = result = scanner.readObject(builderClass.newInstance());
            } catch (Exception e) {
                throw new BtParseException("Failed to read from encoded data", e);
            }
        }
        return result;
    }

    private void assertType(BEType type) {

        if (type == null) {
            throw new NullPointerException("Invalid type -- null");
        }

        if (this.type != type) {
            throw new IllegalStateException(
                    "Can't read " + type.name().toLowerCase() + " from: " + this.type.name().toLowerCase());
        }
    }

    @Override
    public void close() {
        if (scanner != null) {
            scanner.close();
        }
    }
}
