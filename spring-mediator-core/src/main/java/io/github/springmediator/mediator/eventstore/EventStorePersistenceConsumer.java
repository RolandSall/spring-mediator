package io.github.springmediator.mediator.eventstore;

import io.github.springmediator.mediator.annotations.DomainEvent;
import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.core.IEvent;
import io.github.springmediator.mediator.core.IEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * System consumer that persists events to the event store.
 * Runs in the system phase, before user consumers.
 */
public class EventStorePersistenceConsumer implements IEventConsumer<IEvent> {

    private static final Logger log = LoggerFactory.getLogger(EventStorePersistenceConsumer.class);

    private final IEventStoreRepository repository;
    private final MediatorContextManager contextManager;
    private final String mode;

    public EventStorePersistenceConsumer(IEventStoreRepository repository,
                                         MediatorContextManager contextManager,
                                         String mode) {
        this.repository = repository;
        this.contextManager = contextManager;
        this.mode = mode;
    }

    @Override
    public void handle(IEvent event) {
        StoredEvent storedEvent = buildStoredEvent(event);

        AggregateInfo aggregateInfo = extractAggregateInfo(event);
        if (aggregateInfo != null) {
            storedEvent.setAggregateType(aggregateInfo.type());
            storedEvent.setAggregateId(aggregateInfo.id());
        }

        if ("source".equals(mode) && aggregateInfo != null) {
            long nextSeq = repository.getNextSequence(aggregateInfo.type(), aggregateInfo.id());
            storedEvent.setSequenceNumber(nextSeq);
        }

        repository.saveEvent(storedEvent);
    }

    private StoredEvent buildStoredEvent(IEvent event) {
        StoredEvent stored = new StoredEvent();
        stored.setEventId(UUID.randomUUID().toString());
        stored.setEventType(event.getClass().getSimpleName());
        stored.setPayload(eventToMap(event));
        stored.setOccurredAt(Instant.now());
        stored.setStoredAt(Instant.now());

        if (contextManager.hasContext()) {
            stored.setCorrelationId(contextManager.getCorrelationId());
            stored.setCausationId(contextManager.getCausationId());
        }

        return stored;
    }

    private AggregateInfo extractAggregateInfo(IEvent event) {
        DomainEvent annotation = event.getClass().getAnnotation(DomainEvent.class);
        if (annotation == null) {
            return null;
        }

        try {
            Field field = event.getClass().getDeclaredField(annotation.aggregateIdField());
            field.setAccessible(true);
            Object idValue = field.get(event);
            if (idValue != null) {
                return new AggregateInfo(annotation.aggregateType(), idValue.toString());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.warn("Could not extract aggregate ID from field '{}' on event {}: {}",
                    annotation.aggregateIdField(), event.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    private Map<String, Object> eventToMap(IEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : event.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                map.put(field.getName(), field.get(event));
            } catch (IllegalAccessException e) {
                log.warn("Could not read field {} on event {}", field.getName(),
                        event.getClass().getSimpleName());
            }
        }
        return map;
    }

    private record AggregateInfo(String type, String id) {}
}
