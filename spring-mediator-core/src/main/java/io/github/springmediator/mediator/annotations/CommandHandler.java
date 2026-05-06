package io.github.springmediator.mediator.annotations;

import io.github.springmediator.mediator.core.ICommand;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command handler. The annotated class must implement
 * {@link io.github.springmediator.mediator.core.ICommandHandler}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface CommandHandler {

    /**
     * The command class this handler processes.
     */
    Class<? extends ICommand> value();
}
