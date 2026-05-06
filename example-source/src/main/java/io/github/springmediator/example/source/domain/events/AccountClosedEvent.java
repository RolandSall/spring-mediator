package io.github.springmediator.example.source.domain.events;

import io.github.springmediator.mediator.core.IEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountClosedEvent implements IEvent {
    private final String accountId;
    private final String reason;
}
