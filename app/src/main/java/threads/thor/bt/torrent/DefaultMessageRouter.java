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

package threads.thor.bt.torrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import threads.thor.bt.IAgent;
import threads.thor.bt.IConsumers;
import threads.thor.bt.IProduces;
import threads.thor.bt.protocol.Message;

public class DefaultMessageRouter implements MessageRouter {

    private final Object changesLock;
    private final List<MessageConsumer<Message>> genericConsumers;
    private final Map<Class<?>, Collection<MessageConsumer<?>>> typedConsumers;
    private final List<MessageProducer> producers;
    // collection of added consumers/producers in the form of runnable "commands"..
    // quick and dirty!
    private final List<Runnable> changes;

    public DefaultMessageRouter() {
        this(Collections.emptyList());
    }

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

    @Override
    public void unregisterMessagingAgent(IAgent agent) {
        // TODO
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
