package threads.thor.bt.bencoding;

import java.math.BigInteger;

import threads.thor.bt.bencoding.model.BEInteger;

class BEIntegerBuilder extends BEPrefixedTypeBuilder<BEInteger> {

    private final StringBuilder buf;

    BEIntegerBuilder() {
        buf = new StringBuilder();
    }

    @Override
    protected boolean doAccept(int b) {

        char c = (char) b;
        if (Character.isDigit(c) || buf.length() == 0 && c == '-') {
            buf.append(c);
            return true;
        }
        throw new IllegalArgumentException("Unexpected token while reading integer (as ASCII char): " + c);
    }

    @Override
    protected boolean acceptEOF() {
        return true;
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    protected BEInteger doBuild(byte[] content) {
        return new BEInteger(content, new BigInteger(buf.toString()));
    }
}
