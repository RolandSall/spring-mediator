package io.github.springmediator.mediator.aggregate;

import io.github.springmediator.mediator.core.IEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for aggregate roots in event sourcing.
 * Events are applied using convention: apply{EventClassName}(event)
 *
 * @param <TId> the type of aggregate ID
 */
public abstract class AggregateRoot<TId> {

    private static final Logger log = LoggerFactory.getLogger(AggregateRoot.class);

    private final List<IEvent> uncommittedEvents = new ArrayList<>();
    private long version = 0;

    public abstract TId getId();
    public abstract String getAggregateType();

    public long getVersion() {
        return version;
    }

    public List<IEvent> getUncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    /**
     * Replay historical events to rebuild aggregate state.
     */
    public void loadFromHistory(List<IEvent> events) {
        for (IEvent event : events) {
            applyEvent(event, false);
        }
    }

    /**
     * Apply a new event — tracks as uncommitted and increments version.
     */
    protected void apply(IEvent event) {
        applyEvent(event, true);
    }

    private void applyEvent(IEvent event, boolean isNew) {
        String methodName = "apply" + event.getClass().getSimpleName();
        try {
            Method method = this.getClass().getDeclaredMethod(methodName, event.getClass());
            method.setAccessible(true);
            method.invoke(this, event);
        } catch (NoSuchMethodException e) {
            log.warn("No handler method '{}' found on aggregate {}", methodName, getAggregateType());
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply event " + event.getClass().getSimpleName()
                    + " to aggregate " + getAggregateType(), e);
        }

        if (isNew) {
            uncommittedEvents.add(event);
        }
        version++;
    }
}
