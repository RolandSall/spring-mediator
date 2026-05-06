package io.github.springmediator.example.audit.application.order.commands;

import io.github.springmediator.mediator.core.ICommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class CreateOrderCommand implements ICommand {

    @NotBlank(message = "Customer ID is required")
    private final String customerId;

    @NotEmpty(message = "At least one item is required")
    private final List<Item> items;

    @Positive(message = "Total must be positive")
    private final BigDecimal total;

    public record Item(String productId, String name, int quantity, BigDecimal price) {}
}
