package io.github.springmediator.mediator.annotations;

import io.github.springmediator.mediator.pipeline.IPipelineBehavior;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When placed on a command or query class, the specified pipeline behaviors
 * will be skipped during execution.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipBehavior {

    /**
     * The behavior classes to skip.
     */
    Class<? extends IPipelineBehavior<?, ?>>[] value();
}
