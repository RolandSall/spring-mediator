package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.core.IEventConsumer;

public class RegisteredEventConsumer {

    private final IEventConsumer<?> instance;
    private final EventCriticality criticality;
    private final int order;

    public RegisteredEventConsumer(IEventConsumer<?> instance, EventCriticality criticality, int order) {
        this.instance = instance;
        this.criticality = criticality;
        this.order = order;
    }

    public IEventConsumer<?> getInstance() { return instance; }
    public EventCriticality getCriticality() { return criticality; }
    public int getOrder() { return order; }
    public String getName() { return instance.getClass().getSimpleName(); }
}
