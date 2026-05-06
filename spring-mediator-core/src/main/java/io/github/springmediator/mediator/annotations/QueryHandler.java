package io.github.springmediator.mediator.annotations;

import io.github.springmediator.mediator.core.IQuery;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a query handler. The annotated class must implement
 * {@link io.github.springmediator.mediator.core.IQueryHandler}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface QueryHandler {

    /**
     * The query class this handler processes.
     */
    Class<? extends IQuery<?>> value();
}
