package io.github.springmediator.example.source.application.account.commands;

import io.github.springmediator.mediator.core.ICommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class WithdrawMoneyCommand implements ICommand {

    @NotBlank(message = "Account ID is required")
    private final String accountId;

    @Positive(message = "Withdrawal amount must be positive")
    private final BigDecimal amount;
}
