package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.core.*;
import io.github.springmediator.mediator.pipeline.RegisteredBehavior;

import java.util.List;
import java.util.Map;

/**
 * Facade over CommandBus, QueryBus, and EventBus.
 * Entry point for sending commands, queries, and publishing events.
 */
public class MediatorBus {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final EventBus eventBus;
    private final MediatorContextManager contextManager;
    private final HandlerRegistry registry;

    public MediatorBus(CommandBus commandBus, QueryBus queryBus, EventBus eventBus,
                       MediatorContextManager contextManager,
                       HandlerRegistry registry) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.eventBus = eventBus;
        this.contextManager = contextManager;
        this.registry = registry;
    }

    public <T extends ICommand> void send(T command) {
        contextManager.runWithNewContext(() -> commandBus.send(command));
    }

    public <T extends IQuery<R>, R> R query(T query) {
        return contextManager.runWithNewContext(() -> queryBus.query(query));
    }

    public EventPublishResult publish(IEvent event) {
        if (contextManager.hasContext()) {
            return eventBus.publish(event);
        }
        return contextManager.runWithNewContext(() -> eventBus.publish(event));
    }

    public void publishAll(List<? extends IEvent> events) {
        for (IEvent event : events) {
            publish(event);
        }
    }

    // --- Registry access (for introspection and registration) ---

    public HandlerRegistry getRegistry() { return registry; }

    public List<String> getRegisteredCommands() { return registry.getRegisteredCommands(); }
    public Map<String, String> getRegisteredCommandsDetailed() { return registry.getRegisteredCommandsDetailed(); }
    public List<String> getRegisteredQueries() { return registry.getRegisteredQueries(); }
    public Map<String, String> getRegisteredQueriesDetailed() { return registry.getRegisteredQueriesDetailed(); }
    public List<String> getRegisteredEvents() { return registry.getRegisteredEvents(); }
    public List<RegisteredBehavior> getRegisteredBehaviors() { return registry.getRegisteredBehaviors(); }
}
