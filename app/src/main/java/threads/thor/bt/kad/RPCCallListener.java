package threads.thor.bt.kad;

import threads.thor.bt.kad.messages.MessageBase;

/**
 * Class which objects should derive from, if they want to know the result of a call.
 *
 * @author Damokles
 */
public interface RPCCallListener {

    default void stateTransition(RPCCall c, RPCState previous, RPCState current) {
    }

    /**
     * A response was received.
     *
     * @param c   The call
     * @param rsp The response
     */
    default void onResponse(RPCCall c, MessageBase rsp) {
    }


    /**
     * The call has timed out.
     *
     * @param c The call
     */
    default void onTimeout(RPCCall c) {
    }
}
