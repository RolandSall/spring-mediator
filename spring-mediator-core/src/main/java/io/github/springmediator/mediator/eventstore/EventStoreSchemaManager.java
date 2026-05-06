package io.github.springmediator.mediator.eventstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Creates the event store table on startup if it doesn't exist.
 */
public class EventStoreSchemaManager {

    private static final Logger log = LoggerFactory.getLogger(EventStoreSchemaManager.class);

    private final DataSource dataSource;
    private final String tableName;

    public EventStoreSchemaManager(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = tableName;
    }

    public void initialize() {
        log.info("Initializing event store schema (table: {})", tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "event_id VARCHAR(36) PRIMARY KEY, " +
                    "event_type VARCHAR(255) NOT NULL, " +
                    "payload JSONB NOT NULL, " +
                    "occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
                    "stored_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
                    "correlation_id VARCHAR(36), " +
                    "causation_id VARCHAR(36), " +
                    "metadata JSONB DEFAULT '{}', " +
                    "aggregate_type VARCHAR(255), " +
                    "aggregate_id VARCHAR(255), " +
                    "sequence_number BIGINT" +
                    ")");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_" + tableName + "_aggregate_sequence " +
                    "ON " + tableName + " (aggregate_type, aggregate_id, sequence_number) " +
                    "WHERE sequence_number IS NOT NULL");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_aggregate " +
                    "ON " + tableName + " (aggregate_type, aggregate_id, sequence_number)");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_type " +
                    "ON " + tableName + " (event_type)");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_correlation " +
                    "ON " + tableName + " (correlation_id)");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_occurred " +
                    "ON " + tableName + " (occurred_at)");

            log.info("Event store schema initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize event store schema: {}", e.getMessage(), e);
            throw new RuntimeException("Event store schema initialization failed", e);
        }
    }
}
