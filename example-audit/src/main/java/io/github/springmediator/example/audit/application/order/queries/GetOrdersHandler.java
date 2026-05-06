package io.github.springmediator.example.audit.application.order.queries;

import io.github.springmediator.example.audit.domain.entities.Order;
import io.github.springmediator.example.audit.infrastructure.persistence.OrderPersistor;
import io.github.springmediator.mediator.annotations.QueryHandler;
import io.github.springmediator.mediator.core.IQueryHandler;

import java.util.List;

@QueryHandler(GetOrdersQuery.class)
public class GetOrdersHandler implements IQueryHandler<GetOrdersQuery, List<Order>> {

    private final OrderPersistor orderPersistor;

    public GetOrdersHandler(OrderPersistor orderPersistor) {
        this.orderPersistor = orderPersistor;
    }

    @Override
    public List<Order> execute(GetOrdersQuery query) {
        return orderPersistor.findAll();
    }
}
