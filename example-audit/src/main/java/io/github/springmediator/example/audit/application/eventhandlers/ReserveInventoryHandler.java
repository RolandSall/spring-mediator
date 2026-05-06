package io.github.springmediator.example.audit.application.eventhandlers;

import io.github.springmediator.example.audit.domain.events.InventoryReleasedEvent;
import io.github.springmediator.example.audit.domain.events.OrderPlacedEvent;
import io.github.springmediator.mediator.annotations.Critical;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.ICriticalEventConsumer;
import io.github.springmediator.mediator.core.IEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventHandler(OrderPlacedEvent.class)
@Critical(order = 1)
public class ReserveInventoryHandler implements ICriticalEventConsumer<OrderPlacedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ReserveInventoryHandler.class);

    @Override
    public void handle(OrderPlacedEvent event) {
        log.info("Reserving inventory for order {}", event.getOrderId());
        // Simulate inventory reservation
    }

    @Override
    public IEvent applyCompensatingEvent(OrderPlacedEvent event) {
        log.info("Compensating: releasing inventory for order {}", event.getOrderId());
        return new InventoryReleasedEvent(event.getOrderId());
    }
}
