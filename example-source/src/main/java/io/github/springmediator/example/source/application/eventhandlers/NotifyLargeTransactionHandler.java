package io.github.springmediator.example.source.application.eventhandlers;

import io.github.springmediator.example.source.domain.events.MoneyWithdrawnEvent;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.IEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

@EventHandler(MoneyWithdrawnEvent.class)
public class NotifyLargeTransactionHandler implements IEventConsumer<MoneyWithdrawnEvent> {

    private static final Logger log = LoggerFactory.getLogger(NotifyLargeTransactionHandler.class);
    private static final BigDecimal THRESHOLD = new BigDecimal("10000");

    @Override
    public void handle(MoneyWithdrawnEvent event) {
        if (event.getAmount().compareTo(THRESHOLD) >= 0) {
            log.warn("ALERT: Large withdrawal of {} from account {}", event.getAmount(), event.getAccountId());
        }
    }
}
