package io.github.springmediator.mediator.core;

/**
 * Marker interface for queries. Queries represent a request for data.
 * A query has exactly one handler and returns a result of type R.
 *
 * @param <R> the result type
 */
public interface IQuery<R> {
}
