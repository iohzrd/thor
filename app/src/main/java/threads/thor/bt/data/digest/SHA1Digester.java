package threads.thor.bt.data.digest;

public class SHA1Digester extends JavaSecurityDigester {

    private SHA1Digester(int step) {
        super("SHA-1", step);
    }

    public static SHA1Digester rolling(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("Invalid step: " + step);
        }
        return new SHA1Digester(step);
    }
}
