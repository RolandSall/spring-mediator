package io.github.springmediator.example.source.domain.entities;

import io.github.springmediator.example.source.domain.events.AccountClosedEvent;
import io.github.springmediator.example.source.domain.events.AccountOpenedEvent;
import io.github.springmediator.example.source.domain.events.MoneyDepositedEvent;
import io.github.springmediator.example.source.domain.events.MoneyWithdrawnEvent;
import io.github.springmediator.mediator.aggregate.AggregateRoot;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class BankAccount extends AggregateRoot<String> {

    private String accountId;
    private String ownerName;
    private BigDecimal balance = BigDecimal.ZERO;
    private boolean closed = false;

    public BankAccount() {}

    public static BankAccount open(String accountId, String ownerName, BigDecimal initialDeposit) {
        BankAccount account = new BankAccount();
        account.apply(new AccountOpenedEvent(accountId, ownerName, initialDeposit));
        return account;
    }

    public void deposit(BigDecimal amount) {
        if (closed) throw new IllegalStateException("Cannot deposit to a closed account");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
        apply(new MoneyDepositedEvent(accountId, amount));
    }

    public void withdraw(BigDecimal amount) {
        if (closed) throw new IllegalStateException("Cannot withdraw from a closed account");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Withdrawal amount must be positive");
        if (balance.compareTo(amount) < 0) throw new IllegalStateException("Insufficient funds");
        apply(new MoneyWithdrawnEvent(accountId, amount));
    }

    public void close(String reason) {
        if (closed) throw new IllegalStateException("Account is already closed");
        apply(new AccountClosedEvent(accountId, reason));
    }

    @SuppressWarnings("unused")
    private void applyAccountOpenedEvent(AccountOpenedEvent event) {
        this.accountId = event.getAccountId();
        this.ownerName = event.getOwnerName();
        this.balance = event.getInitialDeposit();
    }

    @SuppressWarnings("unused")
    private void applyMoneyDepositedEvent(MoneyDepositedEvent event) {
        this.balance = this.balance.add(event.getAmount());
    }

    @SuppressWarnings("unused")
    private void applyMoneyWithdrawnEvent(MoneyWithdrawnEvent event) {
        this.balance = this.balance.subtract(event.getAmount());
    }

    @SuppressWarnings("unused")
    private void applyAccountClosedEvent(AccountClosedEvent event) {
        this.closed = true;
    }

    @Override
    public String getId() { return accountId; }

    @Override
    public String getAggregateType() { return "BankAccount"; }
}
