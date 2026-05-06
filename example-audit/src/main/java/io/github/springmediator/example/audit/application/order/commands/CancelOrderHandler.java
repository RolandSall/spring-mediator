package io.github.springmediator.example.audit.application.order.commands;

import io.github.springmediator.example.audit.domain.events.OrderCancelledEvent;
import io.github.springmediator.example.audit.domain.exceptions.OrderNotFoundException;
import io.github.springmediator.example.audit.infrastructure.persistence.OrderPersistor;
import io.github.springmediator.mediator.annotations.CommandHandler;
import io.github.springmediator.mediator.bus.MediatorBus;
import io.github.springmediator.mediator.core.ICommandHandler;

@CommandHandler(CancelOrderCommand.class)
public class CancelOrderHandler implements ICommandHandler<CancelOrderCommand> {

    private final OrderPersistor orderPersistor;
    private final MediatorBus mediatorBus;

    public CancelOrderHandler(OrderPersistor orderPersistor, MediatorBus mediatorBus) {
        this.orderPersistor = orderPersistor;
        this.mediatorBus = mediatorBus;
    }

    @Override
    public void execute(CancelOrderCommand command) {
        boolean updated = orderPersistor.updateStatus(
                command.getOrderId(), "cancelled", command.getReason());

        if (!updated) {
            throw new OrderNotFoundException(command.getOrderId());
        }

        mediatorBus.publish(new OrderCancelledEvent(command.getOrderId(), command.getReason()));
    }
}
