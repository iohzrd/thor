package threads.thor.bt.utils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import threads.thor.bt.kad.DHT;

public class NIOConnectionManager {

    private final ConcurrentLinkedQueue<Selectable> registrations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Selectable> updateInterestOps = new ConcurrentLinkedQueue<>();
    private final List<Selectable> connections = new ArrayList<>();
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();

    private final String name;
    private final HashSet<Selectable> toUpdate = new HashSet<>();
    private Selector selector;
    private volatile boolean wakeupCalled;
    private int iterations;
    private long lastConnectionCheck;
    private int lastNonZeroIteration;


    public NIOConnectionManager(String name) {
        this.name = name;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void selectLoop() {

        iterations = 0;
        lastNonZeroIteration = 0;

        do {
            try {
                wakeupCalled = false;
                selector.select(100);
                wakeupCalled = false;

                connectionChecks();
                processSelected();
                handleRegistrations();
                updateInterestOps();


            } catch (Exception e) {
                DHT.log(e);
            }

            iterations++;

        } while (!suspendOnIdle());
    }

    private void processSelected() throws IOException {
        Set<SelectionKey> keys = selector.selectedKeys();
        for (SelectionKey selKey : keys) {
            Selectable connection = (Selectable) selKey.attachment();
            connection.selectionEvent(selKey);
        }
        keys.clear();
    }

    /*
     * checks if connections need to be removed from the selector
     */
    private void connectionChecks() throws IOException {
        if ((iterations & 0x0F) != 0)
            return;

        long now = System.currentTimeMillis();

        if (now - lastConnectionCheck < 500)
            return;
        lastConnectionCheck = now;

        for (Selectable conn : new ArrayList<>(connections)) {
            conn.doStateChecks();
            SelectableChannel ch = conn.getChannel();
            SelectionKey k;
            if (ch == null || (k = ch.keyFor(selector)) == null || !k.isValid())
                connections.remove(conn);
        }
    }

    private void handleRegistrations() {
        // register new connections
        Selectable toRegister;
        while ((toRegister = registrations.poll()) != null) {
            SelectableChannel ch = toRegister.getChannel();
            SelectionKey key;
            try {
                key = ch.register(selector, toRegister.calcInterestOps(), toRegister);
            } catch (ClosedChannelException ex) {
                // async close
                continue;
            }

            connections.add(toRegister);

        }
    }

    private void updateInterestOps() {
        while (true) {
            Selectable t = updateInterestOps.poll();
            if (t == null)
                break;
            toUpdate.add(t);
        }

        toUpdate.forEach(sel -> {
            SelectionKey k = sel.getChannel().keyFor(selector);
            if (k != null && k.isValid())
                k.interestOps(sel.calcInterestOps());
        });
        toUpdate.clear();
    }

    private boolean suspendOnIdle() {
        if (connections.size() == 0 && registrations.peek() == null) {
            if (iterations - lastNonZeroIteration > 10) {
                workerThread.set(null);
                ensureRunning();
                return true;
            }
            return false;
        }

        lastNonZeroIteration = iterations;

        return false;
    }

    private void ensureRunning() {
        while (true) {
            Thread current = workerThread.get();
            if (current == null && registrations.peek() != null) {
                current = new Thread(this::selectLoop);
                current.setName(name);
                current.setDaemon(true);
                if (workerThread.compareAndSet(null, current)) {
                    current.start();
                    break;
                }
            } else {
                break;
            }
        }
    }


    public void register(Selectable connection) {
        registrations.add(connection);
        ensureRunning();
        selector.wakeup();
    }

    public void interestOpsChanged(Selectable sel) {
        updateInterestOps.add(sel);
        if (Thread.currentThread() != workerThread.get() && !wakeupCalled) {
            wakeupCalled = true;
            selector.wakeup();
        }
    }

}
