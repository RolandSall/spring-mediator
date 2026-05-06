package io.github.springmediator.example.audit.application.order.commands;

import io.github.springmediator.mediator.core.ICommand;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CancelOrderCommand implements ICommand {

    @NotBlank(message = "Order ID is required")
    private final String orderId;

    private final String reason;
}
