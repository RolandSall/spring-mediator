package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.core.*;
import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import io.github.springmediator.mediator.pipeline.PipelineOrchestrator;
import io.github.springmediator.mediator.pipeline.RegisteredBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Central registry of all mediator handlers. Populated once at startup by
 * MediatorHandlerRegistrar, queried at dispatch time by the buses.
 * The handler instances are Spring-managed beans — this is just a lookup index.
 */
public class HandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(HandlerRegistry.class);

    private final Map<String, ICommandHandler<?>> commandHandlers = new LinkedHashMap<>();
    private final Map<String, IQueryHandler<?, ?>> queryHandlers = new LinkedHashMap<>();
    private final Map<String, List<RegisteredEventConsumer>> eventConsumers = new LinkedHashMap<>();
    private final List<SystemConsumer> systemConsumers = new ArrayList<>();
    private final PipelineOrchestrator pipelineOrchestrator;

    public HandlerRegistry(PipelineOrchestrator pipelineOrchestrator) {
        this.pipelineOrchestrator = pipelineOrchestrator;
    }

    // --- Registration (called once at startup by MediatorHandlerRegistrar) ---

    public void registerCommandHandler(Class<? extends ICommand> commandClass,
                                       ICommandHandler<?> handler) {
        String key = commandClass.getSimpleName();
        if (commandHandlers.containsKey(key)) {
            log.warn("Overriding existing command handler for: {}", key);
        }
        commandHandlers.put(key, handler);
    }

    public void registerQueryHandler(Class<? extends IQuery<?>> queryClass,
                                     IQueryHandler<?, ?> handler) {
        String key = queryClass.getSimpleName();
        if (queryHandlers.containsKey(key)) {
            log.warn("Overriding existing query handler for: {}", key);
        }
        queryHandlers.put(key, handler);
    }

    public void registerEventConsumer(Class<? extends IEvent> eventClass,
                                      IEventConsumer<?> handler,
                                      EventCriticality criticality, int order) {
        String key = eventClass.getSimpleName();
        eventConsumers.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new RegisteredEventConsumer(handler, criticality, order));
    }

    public void registerSystemConsumer(IEventConsumer<?> consumer, String name) {
        systemConsumers.add(new SystemConsumer(consumer,
                name != null ? name : consumer.getClass().getSimpleName()));
    }

    public void registerPipelineBehavior(RegisteredBehavior behavior) {
        pipelineOrchestrator.registerBehavior(behavior);
    }

    // --- Lookup (called at dispatch time by buses) ---

    @SuppressWarnings("unchecked")
    public <T extends ICommand> ICommandHandler<T> getCommandHandler(String commandName) {
        return (ICommandHandler<T>) commandHandlers.get(commandName);
    }

    @SuppressWarnings("unchecked")
    public <T extends IQuery<R>, R> IQueryHandler<T, R> getQueryHandler(String queryName) {
        return (IQueryHandler<T, R>) queryHandlers.get(queryName);
    }

    public List<RegisteredEventConsumer> getEventConsumers(String eventName) {
        return eventConsumers.getOrDefault(eventName, Collections.emptyList());
    }

    public List<SystemConsumer> getSystemConsumers() {
        return Collections.unmodifiableList(systemConsumers);
    }

    // --- Introspection ---

    public List<String> getRegisteredCommands() {
        return new ArrayList<>(commandHandlers.keySet());
    }

    public Map<String, String> getRegisteredCommandsDetailed() {
        Map<String, String> result = new LinkedHashMap<>();
        commandHandlers.forEach((cmd, h) -> result.put(cmd, h.getClass().getSimpleName()));
        return result;
    }

    public List<String> getRegisteredQueries() {
        return new ArrayList<>(queryHandlers.keySet());
    }

    public Map<String, String> getRegisteredQueriesDetailed() {
        Map<String, String> result = new LinkedHashMap<>();
        queryHandlers.forEach((q, h) -> result.put(q, h.getClass().getSimpleName()));
        return result;
    }

    public List<String> getRegisteredEvents() {
        return new ArrayList<>(eventConsumers.keySet());
    }

    public List<RegisteredBehavior> getRegisteredBehaviors() {
        return pipelineOrchestrator.getRegisteredBehaviors();
    }
}
