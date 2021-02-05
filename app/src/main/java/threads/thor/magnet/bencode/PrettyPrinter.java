package threads.thor.magnet.bencode;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class PrettyPrinter {

    private final StringBuilder builder;


    public PrettyPrinter(StringBuilder b) {
        builder = b;
    }

    private static boolean containsControls(String st) {
        return st.codePoints().anyMatch(i -> i < 32 && i != '\r' && i != '\n');
    }


    @NonNull
    @Override
    public String toString() {
        return builder.toString();
    }

    void prettyPrintInternal(Object o) {
        if (o instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) o;

            builder.append("{");


            Iterator<Entry<Object, Object>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                prettyPrintInternal(e.getKey());
                builder.append(":");
                prettyPrintInternal(e.getValue());
                if (it.hasNext()) {
                    builder.append(", ");
                }
            }

            builder.append("}");
            return;
        }

        if (o instanceof List) {
            List<?> l = (List<?>) o;
            builder.append("[");

            Iterator<?> it = l.iterator();

            Object prev = null;

            while (it.hasNext()) {
                Object e = it.next();
                if (prev != null) {
                    builder.append(", ");
                }
                prettyPrintInternal(e);
                prev = e;
            }

            builder.append("]");
            return;
        }

        if (o instanceof String) {
            String str = (String) o;
            if (containsControls(str)) {
                prettyPrintInternal(str.getBytes(StandardCharsets.ISO_8859_1));
                return;
            }

            builder.append('"');
            builder.append(str);
            builder.append('"');
            return;
        }

        if (o instanceof Long || o instanceof Integer) {
            builder.append(o);
            return;
        }

        if (o instanceof ByteBuffer) {
            ByteBuffer buf = ((ByteBuffer) o).slice();
            byte[] bytes;
            if (buf.hasArray() && buf.arrayOffset() == 0 && buf.capacity() == buf.limit())
                bytes = buf.array();
            else {
                bytes = new byte[buf.remaining()];
                buf.get(bytes);
            }
            o = bytes;
        }

        if (o instanceof byte[]) {
            byte[] bytes = (byte[]) o;
            if (bytes.length == 0) {
                builder.append("\"\"");
                return;
            }


            builder.append("0x");

            Utils.toHex(bytes, builder, Integer.MAX_VALUE);


            if (bytes.length < 10) {
                builder.append('/');
                builder.append(Utils.stripToAscii(bytes));
            }

            return;
        }

        builder.append("unhandled type(").append(o).append(')');
    }

}
