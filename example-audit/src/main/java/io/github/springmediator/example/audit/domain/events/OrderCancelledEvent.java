package io.github.springmediator.example.audit.domain.events;

import io.github.springmediator.mediator.core.IEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCancelledEvent implements IEvent {
    private final String orderId;
    private final String reason;
}
