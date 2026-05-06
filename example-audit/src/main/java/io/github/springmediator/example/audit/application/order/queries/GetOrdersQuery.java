package io.github.springmediator.example.audit.application.order.queries;

import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.mediator.core.IQuery;

import java.util.List;

public class GetOrdersQuery implements IQuery<List<Order>> {
}
