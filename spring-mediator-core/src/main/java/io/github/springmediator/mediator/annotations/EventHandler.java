package io.github.springmediator.mediator.annotations;

import io.github.springmediator.mediator.core.IEvent;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an event consumer. The annotated class must implement
 * {@link io.github.springmediator.mediator.core.IEventConsumer}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface EventHandler {

    /**
     * The event class this consumer handles.
     */
    Class<? extends IEvent> value();
}
