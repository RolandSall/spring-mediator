package io.github.springmediator.example.source.application.eventhandlers;

import io.github.springmediator.example.source.domain.events.AccountOpenedEvent;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.IEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventHandler(AccountOpenedEvent.class)
public class NotifyAccountOpenedHandler implements IEventConsumer<AccountOpenedEvent> {

    private static final Logger log = LoggerFactory.getLogger(NotifyAccountOpenedHandler.class);

    @Override
    public void handle(AccountOpenedEvent event) {
        log.info("Account opened: {} for owner '{}' with initial deposit {}",
                event.getAccountId(), event.getOwnerName(), event.getInitialDeposit());
    }
}
