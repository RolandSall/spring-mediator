package io.github.springmediator.example.audit.presentation;

import io.github.springmediator.example.audit.application.order.commands.CancelOrderCommand;
import io.github.springmediator.example.audit.application.order.commands.CreateOrderCommand;
import io.github.springmediator.example.audit.application.order.queries.GetOrderQuery;
import io.github.springmediator.example.audit.application.order.queries.GetOrdersQuery;
import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.mediator.bus.MediatorBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class OrderController {

    private final MediatorBus mediatorBus;

    public OrderController(MediatorBus mediatorBus) {
        this.mediatorBus = mediatorBus;
    }

    @GetMapping
    public Map<String, String> index() {
        return Map.of(
                "mode", "audit",
                "description", "Spring Mediator Example — Audit Mode"
        );
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody CreateOrderRequest request) {
        List<CreateOrderCommand.Item> items = request.items().stream()
                .map(i -> new CreateOrderCommand.Item(i.productId(), i.name(), i.quantity(), i.price()))
                .toList();

        mediatorBus.send(new CreateOrderCommand(request.customerId(), items, request.total()));

        return ResponseEntity.ok(Map.of("status", "order_placed"));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(
            @PathVariable String id, @RequestBody(required = false) CancelRequest request) {
        String reason = request != null ? request.reason() : "No reason provided";
        mediatorBus.send(new CancelOrderCommand(id, reason));
        return ResponseEntity.ok(Map.of("status", "order_cancelled"));
    }

    @GetMapping("/orders")
    public List<Order> getOrders() {
        return mediatorBus.query(new GetOrdersQuery());
    }

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable String id) {
        return mediatorBus.query(new GetOrderQuery(id));
    }

    // --- Request DTOs ---

    public record CreateOrderRequest(
            String customerId,
            List<ItemRequest> items,
            BigDecimal total
    ) {}

    public record ItemRequest(String productId, String name, int quantity, BigDecimal price) {}

    public record CancelRequest(String reason) {}
}
