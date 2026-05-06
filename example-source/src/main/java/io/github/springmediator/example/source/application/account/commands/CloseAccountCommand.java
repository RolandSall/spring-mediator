package io.github.springmediator.example.source.application.account.commands;

import io.github.springmediator.mediator.core.ICommand;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CloseAccountCommand implements ICommand {

    @NotBlank(message = "Account ID is required")
    private final String accountId;

    private final String reason;
}
