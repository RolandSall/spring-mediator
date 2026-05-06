package io.github.springmediator.mediator.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a pipeline behavior. The annotated class must implement
 * {@link io.github.springmediator.mediator.pipeline.IPipelineBehavior}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface PipelineBehavior {

    /**
     * Priority of this behavior. Lower values execute first (outermost).
     */
    int priority() default 0;

    /**
     * Scope of this behavior.
     */
    BehaviorScope scope() default BehaviorScope.ALL;
}
