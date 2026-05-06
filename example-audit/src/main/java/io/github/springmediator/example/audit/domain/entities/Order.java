package io.github.springmediator.example.audit.domain.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Order {

    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private BigDecimal total;
    private String status;
    private Instant createdAt;
    private Instant cancelledAt;
    private String cancelReason;

    public Order(String orderId, String customerId, List<OrderItem> items, BigDecimal total) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = items;
        this.total = total;
        this.status = "placed";
        this.createdAt = Instant.now();
    }

    public record OrderItem(String productId, String name, int quantity, BigDecimal price) {}
}
