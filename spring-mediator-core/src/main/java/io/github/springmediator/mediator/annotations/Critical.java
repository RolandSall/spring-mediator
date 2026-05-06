package io.github.springmediator.mediator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event consumer as critical. Critical consumers execute sequentially
 * in order and support compensation if a later critical consumer fails.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Critical {

    /**
     * Execution order among critical consumers. Lower values execute first.
     */
    int order() default 0;
}
