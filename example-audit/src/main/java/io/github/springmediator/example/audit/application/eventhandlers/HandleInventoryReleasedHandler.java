package io.github.springmediator.example.audit.application.eventhandlers;

import io.github.springmediator.example.audit.domain.events.InventoryReleasedEvent;
import io.github.springmediator.mediator.annotations.EventHandler;
import io.github.springmediator.mediator.core.IEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventHandler(InventoryReleasedEvent.class)
public class HandleInventoryReleasedHandler implements IEventConsumer<InventoryReleasedEvent> {

    private static final Logger log = LoggerFactory.getLogger(HandleInventoryReleasedHandler.class);

    @Override
    public void handle(InventoryReleasedEvent event) {
        log.info("Inventory released for order {} — stock restored", event.getOrderId());
    }
}
