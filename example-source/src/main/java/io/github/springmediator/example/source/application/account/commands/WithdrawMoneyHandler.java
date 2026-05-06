package io.github.springmediator.example.source.application.account.commands;

import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.example.source.domain.exceptions.AccountNotFoundException;
import io.github.springmediator.example.source.infrastructure.persistence.BankAccountRepository;
import io.github.springmediator.mediator.annotations.CommandHandler;
import io.github.springmediator.mediator.core.ICommandHandler;

@CommandHandler(WithdrawMoneyCommand.class)
public class WithdrawMoneyHandler implements ICommandHandler<WithdrawMoneyCommand> {

    private final BankAccountRepository repository;

    public WithdrawMoneyHandler(BankAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(WithdrawMoneyCommand command) {
        BankAccount account = repository.findById(command.getAccountId());
        if (account == null) throw new AccountNotFoundException(command.getAccountId());
        account.withdraw(command.getAmount());
        repository.save(account);
    }
}
