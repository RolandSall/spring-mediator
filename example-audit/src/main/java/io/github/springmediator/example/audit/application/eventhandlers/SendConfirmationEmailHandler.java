package io.github.springmediator.example.audit.application.eventhandlers;

import io.github.springmediator.example.audit.domain.events.OrderPlacedEvent;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.IEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventHandler(OrderPlacedEvent.class)
public class SendConfirmationEmailHandler implements IEventConsumer<OrderPlacedEvent> {

    private static final Logger log = LoggerFactory.getLogger(SendConfirmationEmailHandler.class);

    @Override
    public void handle(OrderPlacedEvent event) {
        log.info("Sending confirmation email for order {} to customer {}",
                event.getOrderId(), event.getCustomerId());
        // Simulate email sending
    }
}
