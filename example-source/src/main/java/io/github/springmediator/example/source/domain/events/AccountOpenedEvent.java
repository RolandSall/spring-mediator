package io.github.springmediator.example.source.domain.events;

import io.github.springmediator.mediator.core.IEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AccountOpenedEvent implements IEvent {
    private final String accountId;
    private final String ownerName;
    private final BigDecimal initialDeposit;
}
