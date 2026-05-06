package io.github.springmediator.mediator.annotations;

import io.github.springmediator.mediator.aggregate.AggregateRoot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository as handling a specific aggregate type.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ForAggregate {

    /**
     * The aggregate class this repository manages.
     */
    Class<? extends AggregateRoot<?>> value();
}
