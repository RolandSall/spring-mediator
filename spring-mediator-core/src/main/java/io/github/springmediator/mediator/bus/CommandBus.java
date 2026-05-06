package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.annotations.BehaviorScope;
import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.core.ICommand;
import io.github.springmediator.mediator.core.ICommandHandler;
import io.github.springmediator.mediator.exceptions.HandlerNotFoundException;
import io.github.springmediator.mediator.flow.StepEmitter;
import io.github.springmediator.mediator.pipeline.PipelineOrchestrator;

/**
 * Dispatches commands through the pipeline to the appropriate handler.
 * Handler lookup is delegated to HandlerRegistry.
 */
public class CommandBus {

    private final HandlerRegistry registry;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final StepEmitter stepEmitter;
    private final MediatorContextManager contextManager;

    public CommandBus(HandlerRegistry registry,
                      PipelineOrchestrator pipelineOrchestrator,
                      StepEmitter stepEmitter,
                      MediatorContextManager contextManager) {
        this.registry = registry;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.stepEmitter = stepEmitter;
        this.contextManager = contextManager;
    }

    public <T extends ICommand> void send(T command) {
        String commandName = command.getClass().getSimpleName();

        ICommandHandler<T> handler = registry.getCommandHandler(commandName);
        if (handler == null) {
            throw new HandlerNotFoundException(commandName, "command");
        }

        if (stepEmitter != null) {
            stepEmitter.emit(StepEmitter.StepType.COMMAND_DISPATCHED, commandName);
        }

        contextManager.getContext().setCurrentHandlerName(handler.getClass().getSimpleName());

        pipelineOrchestrator.execute(command, BehaviorScope.COMMAND, () -> {
            String handlerName = handler.getClass().getSimpleName();
            if (stepEmitter != null) {
                stepEmitter.emit(StepEmitter.StepType.COMMAND_HANDLER_STARTED, handlerName);
            }
            try {
                handler.execute(command);
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.COMMAND_HANDLER_COMPLETED, handlerName);
                }
            } catch (Exception e) {
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.COMMAND_HANDLER_FAILED, handlerName, e);
                }
                throw e;
            }
            return null;
        });
    }
}
