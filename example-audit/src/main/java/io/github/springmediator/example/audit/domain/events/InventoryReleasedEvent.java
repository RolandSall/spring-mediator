package io.github.springmediator.example.audit.domain.events;

import io.github.springmediator.mediator.core.IEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InventoryReleasedEvent implements IEvent {
    private final String orderId;
}
