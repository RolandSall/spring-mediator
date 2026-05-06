package io.github.springmediator.example.audit.domain.events;

import io.github.springmediator.mediator.core.IEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OrderPlacedEvent implements IEvent {
    private final String orderId;
    private final String customerId;
    private final BigDecimal total;
}
