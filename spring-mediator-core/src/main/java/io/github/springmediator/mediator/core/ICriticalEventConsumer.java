package io.github.springmediator.mediator.core;

/**
 * A critical event consumer that supports compensation.
 * If a critical consumer fails after other critical consumers have succeeded,
 * compensation is triggered in reverse order.
 *
 * @param <T> the event type
 */
public interface ICriticalEventConsumer<T extends IEvent> extends IEventConsumer<T> {

    /**
     * Produce a compensating event to undo the side effects of this consumer.
     * This is the preferred compensation strategy.
     *
     * @param event the original event
     * @return a compensating event to publish, or null if no compensation needed
     */
    default IEvent applyCompensatingEvent(T event) {
        return null;
    }

    /**
     * Direct compensation logic. Use {@link #applyCompensatingEvent(IEvent)} instead when possible.
     *
     * @param event the original event
     */
    default void compensate(T event) {
    }
}
