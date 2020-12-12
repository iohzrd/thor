/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.net;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import threads.LogUtils;
import threads.thor.bt.module.PeerConnectionSelector;
import threads.thor.bt.net.pipeline.ChannelHandlerContext;
import threads.thor.bt.service.RuntimeLifecycleBinder;

public class DataReceivingLoop implements Runnable, DataReceiver {
    private static final String TAG = DataReceivingLoop.class.getSimpleName();
    private static final int NO_OPS = 0;

    private final SharedSelector selector;
    private final ConcurrentMap<SelectableChannel, Integer> interestOpsUpdates;

    private volatile boolean shutdown;


    public DataReceivingLoop(@PeerConnectionSelector SharedSelector selector,
                             RuntimeLifecycleBinder lifecycleBinder) {
        this.selector = selector;
        this.interestOpsUpdates = new ConcurrentHashMap<>();

        schedule(lifecycleBinder);
    }

    private void schedule(RuntimeLifecycleBinder lifecycleBinder) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.data-receiver"));
        lifecycleBinder.onStartup("Initialize message receiver", () -> executor.execute(this));
        lifecycleBinder.onShutdown("Shutdown message receiver", () -> {
            try {
                shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Override
    public void registerChannel(SelectableChannel channel, ChannelHandlerContext context) {
        // use atomic wakeup-and-register to prevent blocking of registration,
        // if selection is resumed before call to register is performed
        // (there is a race between the message receiving loop and current thread)
        // TODO: move this to the main loop instead?
        selector.wakeupAndRegister(channel, SelectionKey.OP_READ, context);
    }

    @Override
    public void unregisterChannel(SelectableChannel channel) {
        selector.keyFor(channel).ifPresent(SelectionKey::cancel);
    }

    @Override
    public void activateChannel(SelectableChannel channel) {
        updateInterestOps(channel, SelectionKey.OP_READ);
    }

    @Override
    public void deactivateChannel(SelectableChannel channel) {
        updateInterestOps(channel, NO_OPS);
    }

    private void updateInterestOps(SelectableChannel channel, int interestOps) {
        interestOpsUpdates.put(channel, interestOps);
    }

    @Override
    public void run() {
        while (!shutdown) {
            if (!selector.isOpen()) {

                break;
            }

            try {
                do {
                    if (!interestOpsUpdates.isEmpty()) {
                        processInterestOpsUpdates();
                    }
                } while (selector.select(1000) == 0);

                while (!shutdown) {
                    Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        try {
                            // do not remove the key if it hasn't been processed,
                            // we'll try again in the next loop iteration
                            if (processKey(selectedKeys.next())) {
                                selectedKeys.remove();
                            }
                        } catch (ClosedSelectorException e) {
                            // selector has been closed, there's no point to continue processing
                            throw e;
                        } catch (Exception e) {

                            selectedKeys.remove();
                        }
                    }
                    if (selector.selectedKeys().isEmpty()) {
                        break;
                    }
                    Thread.sleep(1);
                    if (!interestOpsUpdates.isEmpty()) {
                        processInterestOpsUpdates();
                    }
                    selector.selectNow();
                }
            } catch (ClosedSelectorException | InterruptedException e) {
                return;
            } catch (IOException e) {
                throw new RuntimeException("Unexpected I/O exception when selecting peer connections", e);
            }
        }
    }

    private void processInterestOpsUpdates() {
        Iterator<Map.Entry<SelectableChannel, Integer>> iter = interestOpsUpdates.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<SelectableChannel, Integer> entry = iter.next();
            SelectableChannel channel = entry.getKey();
            int interestOps = entry.getValue();
            try {
                selector.keyFor(entry.getKey())
                        .ifPresent(key -> key.interestOps(interestOps));
            } catch (Exception e) {
                LogUtils.error(TAG, "Failed to set interest ops for channel " + channel + " to " + interestOps, e);
            } finally {
                iter.remove();
            }
        }
    }

    /**
     * @return true, if the key has been processed and can be removed
     */
    private boolean processKey(final SelectionKey key) {
        ChannelHandlerContext handler = getHandlerContext(key);
        if (!key.isValid() || !key.isReadable()) {
            return true;
        }
        return handler.readFromChannel();
    }

    private ChannelHandlerContext getHandlerContext(SelectionKey key) {
        Object obj = key.attachment();
        if (!(obj instanceof ChannelHandlerContext)) {
            throw new RuntimeException("Unexpected attachment in selection key: " + obj);
        }
        return (ChannelHandlerContext) obj;
    }

    private void shutdown() {
        shutdown = true;
    }
}
