package io.github.springmediator.example.audit.application.order.queries;

import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.example.audit.domain.exceptions.OrderNotFoundException;
import io.github.springmediator.example.audit.infrastructure.persistence.OrderPersistor;
import io.github.springmediator.mediator.annotations.QueryHandler;
import io.github.springmediator.mediator.core.IQueryHandler;

@QueryHandler(GetOrderQuery.class)
public class GetOrderHandler implements IQueryHandler<GetOrderQuery, Order> {

    private final OrderPersistor orderPersistor;

    public GetOrderHandler(OrderPersistor orderPersistor) {
        this.orderPersistor = orderPersistor;
    }

    @Override
    public Order execute(GetOrderQuery query) {
        Order order = orderPersistor.findById(query.getOrderId());
        if (order == null) {
            throw new OrderNotFoundException(query.getOrderId());
        }
        return order;
    }
}
