package threads.lite.format;

import androidx.annotation.NonNull;

public class Stage {
    private final NavigableNode node;
    private int index;

    Stage(@NonNull NavigableNode node, int index) {
        this.node = node;
        this.index = index;
    }

    public NavigableNode getNode() {
        return node;
    }

    public void incrementIndex() {
        index = index + 1;
    }

    public int index() {
        return index;
    }

    public void setIndex(int value) {
        index = value;
    }

    public Stage copy() {
        return new Stage(node, index);
    }

    @NonNull
    @Override
    public String toString() {
        return node.toString() + " " + index + " ";
    }
}
