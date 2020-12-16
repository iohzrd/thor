package threads.thor.bt.kad.tasks;

import androidx.annotation.NonNull;

import java.util.Objects;

import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.RPCServer;

abstract class TargetedTask extends Task {

    final Key targetKey;


    TargetedTask(Key k, @NonNull RPCServer rpc, Node node) {
        super(rpc, node);
        Objects.requireNonNull(k);
        targetKey = k;
    }

    public Key getTargetKey() {
        return targetKey;
    }

}
