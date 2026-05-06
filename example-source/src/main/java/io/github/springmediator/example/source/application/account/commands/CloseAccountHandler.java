package io.github.springmediator.example.source.application.account.commands;

import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.example.source.domain.exceptions.AccountNotFoundException;
import io.github.springmediator.example.source.infrastructure.persistence.BankAccountRepository;
import io.github.springmediator.mediator.annotations.CommandHandler;
import io.github.springmediator.mediator.core.ICommandHandler;

@CommandHandler(CloseAccountCommand.class)
public class CloseAccountHandler implements ICommandHandler<CloseAccountCommand> {

    private final BankAccountRepository repository;

    public CloseAccountHandler(BankAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(CloseAccountCommand command) {
        BankAccount account = repository.findById(command.getAccountId());
        if (account == null) throw new AccountNotFoundException(command.getAccountId());
        account.close(command.getReason());
        repository.save(account);
    }
}
