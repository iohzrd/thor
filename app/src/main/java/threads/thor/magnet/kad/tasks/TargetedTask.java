package threads.thor.magnet.kad.tasks;

import androidx.annotation.NonNull;

import java.util.Objects;

import threads.thor.magnet.kad.Key;
import threads.thor.magnet.kad.Node;
import threads.thor.magnet.kad.RPCServer;

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
