package io.github.springmediator.mediator.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.springmediator.mediator.exceptions.ConcurrencyException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JPA-based event store repository using native queries.
 * No @Entity scanning — works with any EntityManager the user provides.
 */
public class JpaEventStoreRepository implements IEventStoreRepository {

    private final EntityManager entityManager;
    private final String tableName;
    private final String mode;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JpaEventStoreRepository(EntityManager entityManager, String tableName, String mode) {
        this.entityManager = entityManager;
        this.tableName = tableName;
        this.mode = mode;
    }

    @Override
    public void saveEvent(StoredEvent event) {
        String sql = "INSERT INTO " + tableName +
                " (event_id, event_type, payload, occurred_at, stored_at, correlation_id, " +
                "causation_id, metadata, aggregate_type, aggregate_id, sequence_number) " +
                "VALUES (?1, ?2, ?3::jsonb, ?4, ?5, ?6, ?7, ?8::jsonb, ?9, ?10, ?11)";

        entityManager.createNativeQuery(sql)
                .setParameter(1, event.getEventId())
                .setParameter(2, event.getEventType())
                .setParameter(3, toJson(event.getPayload()))
                .setParameter(4, Timestamp.from(event.getOccurredAt()))
                .setParameter(5, Timestamp.from(event.getStoredAt() != null ? event.getStoredAt() : Instant.now()))
                .setParameter(6, event.getCorrelationId())
                .setParameter(7, event.getCausationId())
                .setParameter(8, toJson(event.getMetadata()))
                .setParameter(9, event.getAggregateType())
                .setParameter(10, event.getAggregateId())
                .setParameter(11, event.getSequenceNumber())
                .executeUpdate();
    }

    @Override
    public void appendEvents(String aggregateType, String aggregateId,
                             List<StoredEvent> events, long expectedVersion) {
        long currentVersion = getNextSequence(aggregateType, aggregateId) - 1;
        if (currentVersion != expectedVersion) {
            throw new ConcurrencyException(aggregateType, aggregateId, expectedVersion, currentVersion);
        }

        long sequence = expectedVersion + 1;
        for (StoredEvent event : events) {
            event.setSequenceNumber(sequence++);
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            saveEvent(event);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StoredEvent> getEventsForAggregate(String aggregateType, String aggregateId) {
        String sql = "SELECT * FROM " + tableName +
                " WHERE aggregate_type = ?1 AND aggregate_id = ?2 ORDER BY sequence_number";

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter(1, aggregateType)
                .setParameter(2, aggregateId)
                .getResultList();

        return rows.stream().map(this::mapRow).toList();
    }

    @Override
    public long getNextSequence(String aggregateType, String aggregateId) {
        String sql = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM " + tableName +
                " WHERE aggregate_type = ?1 AND aggregate_id = ?2";

        Object result = entityManager.createNativeQuery(sql)
                .setParameter(1, aggregateType)
                .setParameter(2, aggregateId)
                .getSingleResult();

        return result != null ? ((Number) result).longValue() : 1;
    }

    private StoredEvent mapRow(Object[] row) {
        StoredEvent event = new StoredEvent();
        event.setEventId((String) row[0]);
        event.setEventType((String) row[1]);
        event.setPayload(fromJson((String) row[2]));
        event.setOccurredAt(((Timestamp) row[3]).toInstant());
        event.setStoredAt(((Timestamp) row[4]).toInstant());
        event.setCorrelationId((String) row[5]);
        event.setCausationId((String) row[6]);
        event.setMetadata(fromJson((String) row[7]));
        event.setAggregateType((String) row[8]);
        event.setAggregateId((String) row[9]);
        event.setSequenceNumber(row[10] != null ? ((Number) row[10]).longValue() : null);
        return event;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }
}
