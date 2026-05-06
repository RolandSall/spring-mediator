package io.github.springmediator.example.source.application.account.queries;

import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.example.source.domain.exceptions.AccountNotFoundException;
import io.github.springmediator.example.source.infrastructure.persistence.BankAccountRepository;
import io.github.springmediator.mediator.annotations.QueryHandler;
import io.github.springmediator.mediator.core.IQueryHandler;

@QueryHandler(GetAccountQuery.class)
public class GetAccountHandler implements IQueryHandler<GetAccountQuery, BankAccount> {

    private final BankAccountRepository repository;

    public GetAccountHandler(BankAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public BankAccount execute(GetAccountQuery query) {
        BankAccount account = repository.findById(query.getAccountId());
        if (account == null) throw new AccountNotFoundException(query.getAccountId());
        return account;
    }
}
