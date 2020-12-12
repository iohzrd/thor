package threads.thor.bt.kad.tasks;

import java.util.Objects;

import threads.thor.bt.kad.Key;
import threads.thor.bt.kad.Node;
import threads.thor.bt.kad.RPCServer;

abstract class TargetedTask extends Task {

    final Key targetKey;


    TargetedTask(Key k, RPCServer rpc, Node node) {
        super(rpc, node);
        Objects.requireNonNull(k);
        targetKey = k;
    }

    public Key getTargetKey() {
        return targetKey;
    }

}
