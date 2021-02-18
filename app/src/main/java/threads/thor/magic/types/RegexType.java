package threads.thor.magic.types;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import threads.thor.magic.entries.MagicFormatter;
import threads.thor.magic.entries.MagicMatcher;

public class RegexType implements MagicMatcher {

    private final static Pattern TYPE_PATTERN = Pattern.compile("[^/]+(/[cs]*)?");
    private static final String EMPTY = "";

    @Override
    public Object convertTestString(String typeStr, String testStr) {
        Matcher matcher = TYPE_PATTERN.matcher(typeStr);
        PatternInfo patternInfo = new PatternInfo();
        if (matcher.matches()) {
            String flagsStr = matcher.group(1);
            if (flagsStr != null && flagsStr.length() > 1) {
                for (char ch : flagsStr.toCharArray()) {
                    if (ch == 'c') {
                        patternInfo.patternFlags |= Pattern.CASE_INSENSITIVE;
                    }
                }
            }
        }
        testStr = PatternUtils.preProcessPattern(testStr);
        patternInfo.pattern = Pattern.compile(".*(" + testStr + ").*", patternInfo.patternFlags);
        return patternInfo;
    }

    @Override
    public Object extractValueFromBytes(int offset, byte[] bytes, boolean required) {
        return EMPTY;
    }

    @Override
    public Object isMatch(Object testValue, Long andValue, boolean unsignedType, Object extractedValue,
                          MutableOffset mutableOffset, byte[] bytes) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
        String line = null;
        int bytesOffset = 0;
        for (int i = 0; i <= mutableOffset.offset; i++) {
            try {
                line = reader.readLine();
                // if eof then no match
                if (line == null) {
                    return null;
                }
                if (i < mutableOffset.offset) {
                    bytesOffset += line.length() + 1;
                }
            } catch (IOException e) {
                // probably won't getData here
                return null;
            }
        }
        if (line == null) {
            // may never getData here
            return null;
        }
        PatternInfo patternInfo = (PatternInfo) testValue;
        Matcher matcher = patternInfo.pattern.matcher(line);
        if (matcher.matches()) {
            mutableOffset.offset = bytesOffset + matcher.end(1);
            return matcher.group(1);
        } else {
            return null;
        }
    }

    @Override
    public void renderValue(StringBuilder sb, Object extractedValue, MagicFormatter formatter) {
        formatter.format(sb, extractedValue);
    }

    @Override
    public byte[] getStartingBytes(Object testValue) {
        return null;
    }

    private static class PatternInfo {
        int patternFlags;
        Pattern pattern;
    }
}
