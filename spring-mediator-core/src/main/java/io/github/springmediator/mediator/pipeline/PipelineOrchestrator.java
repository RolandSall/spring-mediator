package io.github.springmediator.mediator.pipeline;

import io.github.springmediator.mediator.annotations.BehaviorScope;
import io.github.springmediator.mediator.annotations.SkipBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

/**
 * Builds and executes the pipeline behavior chain for commands and queries.
 */
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final List<RegisteredBehavior> behaviors = new ArrayList<>();

    public PipelineOrchestrator(List<RegisteredBehavior> behaviors) {
        if (behaviors != null) {
            this.behaviors.addAll(behaviors);
            this.behaviors.sort(Comparator.comparingInt(RegisteredBehavior::getPriority));
        }
    }

    public void registerBehavior(RegisteredBehavior behavior) {
        behaviors.add(behavior);
        behaviors.sort(Comparator.comparingInt(RegisteredBehavior::getPriority));
    }

    /**
     * Build and execute the pipeline chain for a request.
     *
     * @param request the command or query
     * @param scope   COMMAND or QUERY
     * @param handler the terminal handler that actually processes the request
     * @return the result from the handler
     */
    @SuppressWarnings("unchecked")
    public <TRequest, TResponse> TResponse execute(TRequest request, BehaviorScope scope,
                                                    Supplier<TResponse> handler) {
        // Collect skip list from request class
        Set<Class<?>> skipSet = getSkipSet(request.getClass());

        // Filter applicable behaviors
        List<RegisteredBehavior> applicable = behaviors.stream()
                .filter(b -> b.getScope() == BehaviorScope.ALL || b.getScope() == scope)
                .filter(b -> !skipSet.contains(b.getInstance().getClass()))
                .filter(b -> b.getRequestType() == null || b.getRequestType().isInstance(request))
                .toList();

        if (applicable.isEmpty()) {
            return handler.get();
        }

        // Build chain from right to left (last behavior wraps handler, first behavior is outermost)
        Supplier<TResponse> chain = handler;
        for (int i = applicable.size() - 1; i >= 0; i--) {
            IPipelineBehavior<TRequest, TResponse> behavior =
                    (IPipelineBehavior<TRequest, TResponse>) applicable.get(i).getInstance();
            Supplier<TResponse> next = chain;
            chain = () -> behavior.handle(request, next);
        }

        return chain.get();
    }

    private Set<Class<?>> getSkipSet(Class<?> requestClass) {
        SkipBehavior skip = requestClass.getAnnotation(SkipBehavior.class);
        if (skip == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(skip.value()));
    }

    public List<RegisteredBehavior> getRegisteredBehaviors() {
        return Collections.unmodifiableList(behaviors);
    }
}
