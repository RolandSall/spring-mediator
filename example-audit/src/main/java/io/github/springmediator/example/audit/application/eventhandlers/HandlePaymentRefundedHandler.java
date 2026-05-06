package io.github.springmediator.example.audit.application.eventhandlers;

import io.github.springmediator.example.audit.domain.events.PaymentRefundedEvent;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.IEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventHandler(PaymentRefundedEvent.class)
public class HandlePaymentRefundedHandler implements IEventConsumer<PaymentRefundedEvent> {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentRefundedHandler.class);

    @Override
    public void handle(PaymentRefundedEvent event) {
        log.info("Payment of {} refunded for order {}", event.getAmount(), event.getOrderId());
    }
}
