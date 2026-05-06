package io.github.springmediator.example.source.infrastructure.persistence;

import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.example.source.domain.events.AccountClosedEvent;
import io.github.springmediator.example.source.domain.events.AccountOpenedEvent;
import io.github.springmediator.example.source.domain.events.MoneyDepositedEvent;
import io.github.springmediator.example.source.domain.events.MoneyWithdrawnEvent;
import io.github.springmediator.mediator.aggregate.AggregateRepository;
import io.github.springmediator.mediator.annotations.ForAggregate;
import io.github.springmediator.mediator.bus.MediatorBus;
import io.github.springmediator.mediator.core.IEvent;
import io.github.springmediator.mediator.eventstore.IEventStoreRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@ForAggregate(BankAccount.class)
public class BankAccountRepository extends AggregateRepository<BankAccount, String> {

    public BankAccountRepository(IEventStoreRepository eventStore, MediatorBus mediatorBus) {
        super(eventStore, mediatorBus);
    }

    @Override
    protected BankAccount createEmptyAggregate() {
        return new BankAccount();
    }

    @Override
    protected String getAggregateType() {
        return "BankAccount";
    }

    @Override
    protected IEvent deserializeEvent(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "AccountOpenedEvent" -> new AccountOpenedEvent(
                    (String) payload.get("accountId"),
                    (String) payload.get("ownerName"),
                    new BigDecimal(payload.get("initialDeposit").toString())
            );
            case "MoneyDepositedEvent" -> new MoneyDepositedEvent(
                    (String) payload.get("accountId"),
                    new BigDecimal(payload.get("amount").toString())
            );
            case "MoneyWithdrawnEvent" -> new MoneyWithdrawnEvent(
                    (String) payload.get("accountId"),
                    new BigDecimal(payload.get("amount").toString())
            );
            case "AccountClosedEvent" -> new AccountClosedEvent(
                    (String) payload.get("accountId"),
                    (String) payload.get("reason")
            );
            default -> null;
        };
    }
}
