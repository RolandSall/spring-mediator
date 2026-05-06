package io.github.springmediator.example.source.application.account.commands;

import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.example.source.infrastructure.persistence.BankAccountRepository;
import io.github.springmediator.mediator.annotations.CommandHandler;
import io.github.springmediator.mediator.core.ICommandHandler;

import java.util.UUID;

@CommandHandler(OpenAccountCommand.class)
public class OpenAccountHandler implements ICommandHandler<OpenAccountCommand> {

    private final BankAccountRepository repository;

    public OpenAccountHandler(BankAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(OpenAccountCommand command) {
        String accountId = UUID.randomUUID().toString();
        BankAccount account = BankAccount.open(accountId, command.getOwnerName(), command.getInitialDeposit());
        repository.save(account);
    }
}
