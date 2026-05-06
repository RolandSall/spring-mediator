package io.github.springmediator.mediator.core;

/**
 * Handler for a query. Each query type has exactly one handler.
 *
 * @param <T> the query type
 * @param <R> the result type
 */
public interface IQueryHandler<T extends IQuery<R>, R> {

    R execute(T query);
}
