package io.github.springmediator.example.source.domain.events;

import io.github.springmediator.mediator.core.IEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class MoneyWithdrawnEvent implements IEvent {
    private final String accountId;
    private final BigDecimal amount;
}
