package io.github.springmediator.mediator.eventstore;

import java.util.List;

public interface IEventStoreRepository {

    void saveEvent(StoredEvent event);

    void appendEvents(String aggregateType, String aggregateId,
                      List<StoredEvent> events, long expectedVersion);

    List<StoredEvent> getEventsForAggregate(String aggregateType, String aggregateId);

    long getNextSequence(String aggregateType, String aggregateId);
}
