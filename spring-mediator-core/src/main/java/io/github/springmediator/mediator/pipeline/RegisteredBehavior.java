package io.github.springmediator.mediator.pipeline;

import io.github.springmediator.mediator.annotations.BehaviorScope;

public class RegisteredBehavior {

    private final IPipelineBehavior<?, ?> instance;
    private final int priority;
    private final BehaviorScope scope;
    private final Class<?> requestType;

    public RegisteredBehavior(IPipelineBehavior<?, ?> instance, int priority,
                              BehaviorScope scope, Class<?> requestType) {
        this.instance = instance;
        this.priority = priority;
        this.scope = scope;
        this.requestType = requestType;
    }

    public IPipelineBehavior<?, ?> getInstance() { return instance; }
    public int getPriority() { return priority; }
    public BehaviorScope getScope() { return scope; }
    public Class<?> getRequestType() { return requestType; }
    public String getName() { return instance.getClass().getSimpleName(); }
}
