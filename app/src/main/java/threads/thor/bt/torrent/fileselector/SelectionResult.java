package threads.thor.bt.torrent.fileselector;

public class SelectionResult {

    private final boolean skip;

    private SelectionResult(boolean skip) {
        this.skip = skip;
    }

    public static Builder select() {
        return new Builder();
    }


    public boolean shouldSkip() {
        return skip;
    }

    public static class Builder {

        private Builder() {
        }

        public SelectionResult build() {
            return new SelectionResult(false);
        }
    }
}
