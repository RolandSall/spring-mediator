package io.github.springmediator.mediator.pipeline;

import java.util.function.Supplier;

/**
 * A pipeline behavior that wraps request execution, similar to middleware.
 * Behaviors execute in priority order (lower priority = executes first/outermost).
 *
 * @param <TRequest>  the request type
 * @param <TResponse> the response type
 */
public interface IPipelineBehavior<TRequest, TResponse> {

    /**
     * Handle the request, optionally delegating to the next behavior in the pipeline.
     *
     * @param request the incoming request (command or query)
     * @param next    the next step in the pipeline; call {@code next.get()} to proceed
     * @return the response
     */
    TResponse handle(TRequest request, Supplier<TResponse> next);
}
