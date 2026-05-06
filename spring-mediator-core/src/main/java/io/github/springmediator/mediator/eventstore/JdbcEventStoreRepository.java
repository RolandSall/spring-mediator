package io.github.springmediator.mediator.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.springmediator.mediator.exceptions.ConcurrencyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JdbcEventStoreRepository implements IEventStoreRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final String mode;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcEventStoreRepository(JdbcTemplate jdbcTemplate, String tableName, String mode) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.mode = mode;
    }

    @Override
    public void saveEvent(StoredEvent event) {
        String sql = "INSERT INTO " + tableName +
                " (event_id, event_type, payload, occurred_at, stored_at, correlation_id, " +
                "causation_id, metadata, aggregate_type, aggregate_id, sequence_number) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)";

        jdbcTemplate.update(sql,
                event.getEventId(),
                event.getEventType(),
                toJson(event.getPayload()),
                Timestamp.from(event.getOccurredAt()),
                Timestamp.from(event.getStoredAt() != null ? event.getStoredAt() : Instant.now()),
                event.getCorrelationId(),
                event.getCausationId(),
                toJson(event.getMetadata()),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getSequenceNumber());
    }

    @Override
    public void appendEvents(String aggregateType, String aggregateId,
                             List<StoredEvent> events, long expectedVersion) {
        // Check current version
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
    public List<StoredEvent> getEventsForAggregate(String aggregateType, String aggregateId) {
        String sql = "SELECT * FROM " + tableName +
                " WHERE aggregate_type = ? AND aggregate_id = ? ORDER BY sequence_number";

        return jdbcTemplate.query(sql, this::mapRow, aggregateType, aggregateId);
    }

    @Override
    public long getNextSequence(String aggregateType, String aggregateId) {
        String sql = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM " + tableName +
                " WHERE aggregate_type = ? AND aggregate_id = ?";

        Long result = jdbcTemplate.queryForObject(sql, Long.class, aggregateType, aggregateId);
        return result != null ? result : 1;
    }

    private StoredEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        StoredEvent event = new StoredEvent();
        event.setEventId(rs.getString("event_id"));
        event.setEventType(rs.getString("event_type"));
        event.setPayload(fromJson(rs.getString("payload")));
        event.setOccurredAt(rs.getTimestamp("occurred_at").toInstant());
        event.setStoredAt(rs.getTimestamp("stored_at").toInstant());
        event.setCorrelationId(rs.getString("correlation_id"));
        event.setCausationId(rs.getString("causation_id"));
        event.setMetadata(fromJson(rs.getString("metadata")));
        event.setAggregateType(rs.getString("aggregate_type"));
        event.setAggregateId(rs.getString("aggregate_id"));
        long seq = rs.getLong("sequence_number");
        event.setSequenceNumber(rs.wasNull() ? null : seq);
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
