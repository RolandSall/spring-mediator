package io.github.springmediator.example.audit.application.order.commands;

import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.example.audit.domain.events.OrderPlacedEvent;
import io.github.springmediator.example.audit.infrastructure.persistence.OrderPersistor;
import io.github.springmediator.mediator.annotations.CommandHandler;
import io.github.springmediator.mediator.bus.MediatorBus;
import io.github.springmediator.mediator.core.ICommandHandler;

import java.util.UUID;

@CommandHandler(CreateOrderCommand.class)
public class CreateOrderHandler implements ICommandHandler<CreateOrderCommand> {

    private final OrderPersistor orderPersistor;
    private final MediatorBus mediatorBus;

    public CreateOrderHandler(OrderPersistor orderPersistor, MediatorBus mediatorBus) {
        this.orderPersistor = orderPersistor;
        this.mediatorBus = mediatorBus;
    }

    @Override
    public void execute(CreateOrderCommand command) {
        String orderId = UUID.randomUUID().toString();

        // 1. Save to database (source of truth)
        Order order = new Order(
                orderId,
                command.getCustomerId(),
                command.getItems().stream()
                        .map(i -> new Order.OrderItem(i.productId(), i.name(), i.quantity(), i.price()))
                        .toList(),
                command.getTotal()
        );
        orderPersistor.save(order);

        // 2. Publish event for side effects (audit trail, notifications, etc.)
        mediatorBus.publish(new OrderPlacedEvent(orderId, command.getCustomerId(), command.getTotal()));
    }
}
