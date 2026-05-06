package io.github.springmediator.mediator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly marks an event consumer as non-critical. Non-critical consumers
 * execute asynchronously after all critical consumers complete.
 * This is the default if neither @Critical nor @NonCritical is specified.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NonCritical {
}
