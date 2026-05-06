package io.github.springmediator.example.audit.application.order.queries;

import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.mediator.core.IQuery;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GetOrderQuery implements IQuery<Order> {
    private final String orderId;
}
