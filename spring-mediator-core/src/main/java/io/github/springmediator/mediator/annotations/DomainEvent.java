package io.github.springmediator.mediator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates an event with an aggregate type for event sourcing.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEvent {

    /**
     * The aggregate type this event belongs to.
     */
    String aggregateType();

    /**
     * The field name on the event class that holds the aggregate ID.
     */
    String aggregateIdField();
}
