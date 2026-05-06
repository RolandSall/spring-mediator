package io.github.springmediator.example.source.application.account.queries;

import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.mediator.core.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetAccountQuery implements IQuery<BankAccount> {
    private final String accountId;
}
