package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.annotations.BehaviorScope;
import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.core.IQuery;
import io.github.springmediator.mediator.core.IQueryHandler;
import io.github.springmediator.mediator.exceptions.HandlerNotFoundException;
import io.github.springmediator.mediator.flow.StepEmitter;
import io.github.springmediator.mediator.pipeline.PipelineOrchestrator;

/**
 * Dispatches queries through the pipeline to the appropriate handler.
 * Handler lookup is delegated to HandlerRegistry.
 */
public class QueryBus {

    private final HandlerRegistry registry;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final StepEmitter stepEmitter;
    private final MediatorContextManager contextManager;

    public QueryBus(HandlerRegistry registry,
                    PipelineOrchestrator pipelineOrchestrator,
                    StepEmitter stepEmitter,
                    MediatorContextManager contextManager) {
        this.registry = registry;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.stepEmitter = stepEmitter;
        this.contextManager = contextManager;
    }

    public <T extends IQuery<R>, R> R query(T query) {
        String queryName = query.getClass().getSimpleName();

        IQueryHandler<T, R> handler = registry.getQueryHandler(queryName);
        if (handler == null) {
            throw new HandlerNotFoundException(queryName, "query");
        }

        if (stepEmitter != null) {
            stepEmitter.emit(StepEmitter.StepType.QUERY_DISPATCHED, queryName);
        }

        contextManager.getContext().setCurrentHandlerName(handler.getClass().getSimpleName());

        return pipelineOrchestrator.execute(query, BehaviorScope.QUERY, () -> {
            String handlerName = handler.getClass().getSimpleName();
            if (stepEmitter != null) {
                stepEmitter.emit(StepEmitter.StepType.QUERY_HANDLER_STARTED, handlerName);
            }
            try {
                R result = handler.execute(query);
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.QUERY_HANDLER_COMPLETED, handlerName);
                }
                return result;
            } catch (Exception e) {
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.QUERY_HANDLER_FAILED, handlerName, e);
                }
                throw e;
            }
        });
    }
}
