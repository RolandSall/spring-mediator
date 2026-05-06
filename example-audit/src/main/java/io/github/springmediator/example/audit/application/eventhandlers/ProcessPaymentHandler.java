package io.github.springmediator.example.audit.application.eventhandlers;

import io.github.springmediator.example.audit.domain.events.OrderPlacedEvent;
import io.github.springmediator.example.audit.domain.events.PaymentRefundedEvent;
import io.github.springmediator.mediator.annotations.Critical;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.ICriticalEventConsumer;
import io.github.springmediator.mediator.core.IEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventHandler(OrderPlacedEvent.class)
@Critical(order = 2)
public class ProcessPaymentHandler implements ICriticalEventConsumer<OrderPlacedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentHandler.class);

    @Override
    public void handle(OrderPlacedEvent event) {
        log.info("Processing payment of {} for order {}", event.getTotal(), event.getOrderId());
        // Simulate payment processing
    }

    @Override
    public IEvent applyCompensatingEvent(OrderPlacedEvent event) {
        log.info("Compensating: refunding payment for order {}", event.getOrderId());
        return new PaymentRefundedEvent(event.getOrderId(), event.getTotal());
    }
}
