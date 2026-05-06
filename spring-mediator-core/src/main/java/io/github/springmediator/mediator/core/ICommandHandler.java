package io.github.springmediator.mediator.core;

/**
 * Handler for a command. Each command type has exactly one handler.
 *
 * @param <T> the command type
 */
public interface ICommandHandler<T extends ICommand> {

    void execute(T command);
}
