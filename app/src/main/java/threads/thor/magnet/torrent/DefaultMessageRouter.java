package threads.thor.magnet.torrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import threads.thor.magnet.IAgent;
import threads.thor.magnet.IConsumers;
import threads.thor.magnet.IProduces;
import threads.thor.magnet.protocol.Message;

public class DefaultMessageRouter implements MessageRouter {

    private final Object changesLock;
    private final List<MessageConsumer<Message>> genericConsumers;
    private final Map<Class<?>, Collection<MessageConsumer<?>>> typedConsumers;
    private final List<MessageProducer> producers;
    // collection of added consumers/producers in the form of runnable "commands"..
    // quick and dirty!
    private final List<Runnable> changes;

    public DefaultMessageRouter(Collection<IAgent> messagingAgents) {

        this.genericConsumers = new ArrayList<>();
        this.typedConsumers = new HashMap<>();
        this.producers = new ArrayList<>();

        this.changes = new ArrayList<>();
        this.changesLock = new Object();

        messagingAgents.forEach(this::registerMessagingAgent);
    }

    @Override
    public final void registerMessagingAgent(IAgent agent) {

        if (agent instanceof IConsumers) {
            addConsumers(((IConsumers) agent).getConsumers());
        }

        if (agent instanceof IProduces) {
            List<MessageProducer> list = new ArrayList<>();
            list.add((messageConsumer, context) -> {
                try {
                    ((IProduces) agent).produce(messageConsumer, context);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to invoke message producer", t);
                }
            });
            addProducers(list);
        }
    }

    @SuppressWarnings("unchecked")
    private void addConsumers(List<MessageConsumer<? extends Message>> messageConsumers) {

        List<MessageConsumer<Message>> genericConsumers = new ArrayList<>();
        Map<Class<?>, Collection<MessageConsumer<?>>> typedMessageConsumers = new HashMap<>();

        messageConsumers.forEach(consumer -> {
            Class<?> consumedType = consumer.getConsumedType();
            if (Message.class.equals(consumedType)) {
                genericConsumers.add((MessageConsumer<Message>) consumer);
            } else {
                typedMessageConsumers.computeIfAbsent(consumedType, k -> new ArrayList<>()).add(consumer);
            }
        });

        synchronized (changesLock) {
            this.changes.add(() -> {
                this.genericConsumers.addAll(genericConsumers);
                typedMessageConsumers.keySet().forEach(key -> this.typedConsumers
                        .computeIfAbsent(key, k -> new ArrayList<>()).addAll(typedMessageConsumers.get(key))
                );
            });
        }
    }

    private void addProducers(Collection<MessageProducer> producers) {
        synchronized (changesLock) {
            this.changes.add(() -> this.producers.addAll(producers));
        }
    }

    @Override
    public void consume(Message message, MessageContext context) {
        mergeChanges();
        doConsume(message, context);
    }

    private <T extends Message> void doConsume(T message, MessageContext context) {
        genericConsumers.forEach(consumer -> consumer.consume(message, context));

        Collection<MessageConsumer<?>> consumers = typedConsumers.get(message.getClass());
        if (consumers != null) {
            consumers.forEach(consumer -> {
                @SuppressWarnings("unchecked")
                MessageConsumer<T> typedConsumer = (MessageConsumer<T>) consumer;
                typedConsumer.consume(message, context);
            });
        }
    }

    @Override
    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        mergeChanges();
        producers.forEach(producer -> producer.produce(messageConsumer, context));
    }

    private void mergeChanges() {
        synchronized (changesLock) {
            if (!changes.isEmpty()) {
                changes.forEach(Runnable::run);
                changes.clear();
            }
        }
    }

}
