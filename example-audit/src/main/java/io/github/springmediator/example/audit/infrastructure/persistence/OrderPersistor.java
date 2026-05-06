package io.github.springmediator.example.audit.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.example.audit.domain.entities.Order.OrderItem;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class OrderPersistor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderPersistor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    order_id VARCHAR(36) PRIMARY KEY,
                    customer_id VARCHAR(255) NOT NULL,
                    items JSONB NOT NULL DEFAULT '[]',
                    total NUMERIC(12,2) NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'placed',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    cancelled_at TIMESTAMPTZ,
                    cancel_reason TEXT
                )
                """);
    }

    public void save(Order order) {
        jdbcTemplate.update("""
                INSERT INTO orders (order_id, customer_id, items, total, status, created_at)
                VALUES (?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (order_id) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id,
                    items = EXCLUDED.items,
                    total = EXCLUDED.total,
                    status = EXCLUDED.status
                """,
                order.getOrderId(),
                order.getCustomerId(),
                toJson(order.getItems()),
                order.getTotal(),
                order.getStatus(),
                Timestamp.from(order.getCreatedAt()));
    }

    public Order findById(String orderId) {
        List<Order> results = jdbcTemplate.query(
                "SELECT * FROM orders WHERE order_id = ?",
                this::mapRow, orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Order> findAll() {
        return jdbcTemplate.query("SELECT * FROM orders ORDER BY created_at DESC", this::mapRow);
    }

    public boolean updateStatus(String orderId, String status, String reason) {
        int rows = jdbcTemplate.update("""
                UPDATE orders SET status = ?, cancelled_at = NOW(), cancel_reason = ?
                WHERE order_id = ? AND status != 'cancelled'
                """, status, reason, orderId);
        return rows > 0;
    }

    private Order mapRow(ResultSet rs, int rowNum) throws SQLException {
        Order order = new Order();
        order.setOrderId(rs.getString("order_id"));
        order.setCustomerId(rs.getString("customer_id"));
        order.setItems(fromJson(rs.getString("items")));
        order.setTotal(rs.getBigDecimal("total"));
        order.setStatus(rs.getString("status"));
        order.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        if (cancelledAt != null) {
            order.setCancelledAt(cancelledAt.toInstant());
        }
        order.setCancelReason(rs.getString("cancel_reason"));
        return order;
    }

    private String toJson(List<OrderItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<OrderItem> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
