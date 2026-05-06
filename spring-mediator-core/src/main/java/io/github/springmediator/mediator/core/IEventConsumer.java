package io.github.springmediator.mediator.core;

/**
 * Consumer for an event. Each event type can have multiple consumers.
 *
 * @param <T> the event type
 */
public interface IEventConsumer<T extends IEvent> {

    void handle(T event);
}
