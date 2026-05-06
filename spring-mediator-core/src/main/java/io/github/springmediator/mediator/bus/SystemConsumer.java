package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.core.IEvent;
import io.github.springmediator.mediator.core.IEventConsumer;

/**
 * Wrapper for system-level event consumers (e.g., event store persistence)
 * that run before user consumers.
 */
public class SystemConsumer {

    private final IEventConsumer<IEvent> consumer;
    private final String name;

    @SuppressWarnings("unchecked")
    public SystemConsumer(IEventConsumer<?> consumer, String name) {
        this.consumer = (IEventConsumer<IEvent>) consumer;
        this.name = name;
    }

    public IEventConsumer<IEvent> getConsumer() { return consumer; }
    public String getName() { return name; }
}
