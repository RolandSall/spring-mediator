package io.github.springmediator.mediator.aggregate;

import io.github.springmediator.mediator.bus.MediatorBus;
import io.github.springmediator.mediator.core.IEvent;
import io.github.springmediator.mediator.eventstore.IEventStoreRepository;
import io.github.springmediator.mediator.eventstore.StoredEvent;

import java.util.List;

/**
 * Base repository for aggregate roots using event sourcing.
 * Subclasses must implement aggregate creation and event deserialization.
 *
 * @param <T>   the aggregate type
 * @param <TId> the aggregate ID type
 */
public abstract class AggregateRepository<T extends AggregateRoot<TId>, TId> {

    protected final IEventStoreRepository eventStore;
    protected final MediatorBus mediatorBus;

    protected AggregateRepository(IEventStoreRepository eventStore, MediatorBus mediatorBus) {
        this.eventStore = eventStore;
        this.mediatorBus = mediatorBus;
    }

    /**
     * Create an empty aggregate instance for event replay.
     */
    protected abstract T createEmptyAggregate();

    /**
     * Deserialize a stored event back to a domain event.
     */
    protected abstract IEvent deserializeEvent(String eventType, java.util.Map<String, Object> payload);

    /**
     * Get the aggregate type name.
     */
    protected abstract String getAggregateType();

    public T findById(TId id) {
        List<StoredEvent> storedEvents = eventStore.getEventsForAggregate(
                getAggregateType(), id.toString());

        if (storedEvents.isEmpty()) {
            return null;
        }

        T aggregate = createEmptyAggregate();
        List<IEvent> events = storedEvents.stream()
                .map(se -> deserializeEvent(se.getEventType(), se.getPayload()))
                .filter(java.util.Objects::nonNull)
                .toList();

        aggregate.loadFromHistory(events);
        return aggregate;
    }

    public T getById(TId id) {
        T aggregate = findById(id);
        if (aggregate == null) {
            throw new RuntimeException("Aggregate " + getAggregateType()
                    + " with ID " + id + " not found");
        }
        return aggregate;
    }

    public void save(T aggregate) {
        List<IEvent> uncommitted = aggregate.getUncommittedEvents();
        for (IEvent event : uncommitted) {
            mediatorBus.publish(event);
        }
        aggregate.markEventsAsCommitted();
    }
}
