package threads.thor.magic.types;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import threads.thor.magic.endian.EndianType;
import threads.thor.magic.entries.MagicFormatter;

/**
 * A 4-byte value interpreted as a UNIX-style date, but interpreted as local time rather than UTC.
 *
 * @author graywatson
 */
public class LocalDateType extends IntegerType {

    private final ThreadLocal<SimpleDateFormat> dateFormat =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US));

    public LocalDateType(EndianType endianType) {
        super(endianType);
    }

    @Override
    public void renderValue(StringBuilder sb, Object extractedValue, MagicFormatter formatter) {
        long val = (Long) extractedValue;
        Date date = dateFromExtractedValue(val);
        SimpleDateFormat format = dateFormat.get();
        Objects.requireNonNull(format);
        formatter.format(sb, format.format(date));
    }

    Date dateFromExtractedValue(long val) {
        val *= 1000;
        return new Date(val);
    }

}
