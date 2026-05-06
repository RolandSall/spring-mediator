package io.github.springmediator.example.source.application.account.commands;

import io.github.springmediator.mediator.core.ICommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OpenAccountCommand implements ICommand {

    @NotBlank(message = "Owner name is required")
    private final String ownerName;

    @Positive(message = "Initial deposit must be positive")
    private final BigDecimal initialDeposit;
}
