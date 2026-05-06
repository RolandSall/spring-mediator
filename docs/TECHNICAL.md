# Technical documentation — Spring Mediator internals

This document describes **how the library is built**, class by class. It is the
companion to [FUNCTIONAL.md](./FUNCTIONAL.md), which describes how to **use** it.

> Repo layout, group, version, supported Spring/Java: see [README.md](../README.md).

---

## Table of contents

1. [High-level architecture](#1-high-level-architecture)
2. [Module dependency graph](#2-module-dependency-graph)
3. [Bootstrap sequence](#3-bootstrap-sequence)
4. [Handler registry](#4-handler-registry)
5. [Pipeline orchestrator](#5-pipeline-orchestrator)
6. [Command bus](#6-command-bus)
7. [Query bus](#7-query-bus)
8. [Event bus — three-phase dispatch with compensation](#8-event-bus--three-phase-dispatch-with-compensation)
9. [Mediator context (correlation / causation)](#9-mediator-context-correlation--causation)
10. [Event store](#10-event-store)
11. [Aggregate root and aggregate repository](#11-aggregate-root-and-aggregate-repository)
12. [Built-in pipeline behaviors](#12-built-in-pipeline-behaviors)
13. [Auto-configuration matrix](#13-auto-configuration-matrix)
14. [Flow tracing protocol](#14-flow-tracing-protocol)
15. [Known limitations](#15-known-limitations)

---

## 1. High-level architecture

```
                    ┌──────────────────────────────────────┐
                    │              MediatorBus             │
                    │   (facade + opens MediatorContext)   │
                    └──┬─────────────┬─────────────┬───────┘
                  send │       query │     publish │
                       ▼             ▼             ▼
                 CommandBus      QueryBus       EventBus
                       │             │             │
                       ▼             ▼             ▼
              PipelineOrchestrator (behaviors chain)│
                       │             │             ▼
                       │             │      ┌──────────────────┐
                       │             │      │ system consumers │  ← e.g. event-store persistence
                       │             │      ├──────────────────┤
                       │             │      │ critical (sync,  │  ← @Critical(order=N)
                       │             │      │  ordered, with   │     compensation on failure
                       │             │      │  compensation)   │
                       │             │      ├──────────────────┤
                       │             │      │ non-critical     │  ← virtual-thread executor
                       │             │      │ (async)          │
                       │             │      └──────────────────┘
                       ▼             ▼
              ICommandHandler   IQueryHandler
```

`MediatorBus` is the facade clients use. The three buses share one `HandlerRegistry`
(populated once at startup) and one `PipelineOrchestrator`. Bean discovery is done
once at startup by `MediatorHandlerRegistrar`; nothing introspects beans at dispatch
time.

---

## 2. Module dependency graph

```
spring-mediator-starter           (consumed by user apps)
    └─ api(spring-mediator-autoconfigure)
        └─ api(spring-mediator-core)
            └─ api(spring-context)
            ├─ compileOnly(spring-jdbc)              [event store JDBC]
            ├─ compileOnly(jakarta.persistence-api)  [event store JPA]
            ├─ compileOnly(jakarta.validation-api)   [ValidationBehavior]
            └─ compileOnly(jackson-databind)         [event store payload JSON]
```

- **core** is plain Spring (no Spring Boot dep). All optional integrations are
  `compileOnly` so consumers don't pay for what they don't enable.
- **autoconfigure** depends on `spring-boot-autoconfigure` and registers the
  conditional `@Bean`s and the `BeanPostProcessor`. Its `META-INF/spring/...AutoConfiguration.imports`
  file is what makes Spring Boot discover it without an `@EnableMediator` opt-in.
- **starter** is empty by Spring Boot starter convention — its only job is
  to be the dependency users add. (`spring-boot-starter-jdbc`,
  `spring-boot-starter-validation`, etc. are all "empty" jars too — same pattern.)

---

## 3. Bootstrap sequence

`MediatorHandlerRegistrar` (registered as a bean in `MediatorAutoConfiguration`)
implements both `BeanPostProcessor` and `SmartInitializingSingleton`:

1. **`postProcessAfterInitialization(bean, name)`** — for every bean Spring creates,
   look at its class for `@CommandHandler`, `@QueryHandler`, `@EventHandler`,
   `@PipelineBehavior`. Stash a `PendingRegistration(type, bean, targetClass, extra)`
   into a list. (Don't push into `HandlerRegistry` yet — handler types might depend
   on order of bean creation.)
2. **`afterSingletonsInstantiated()`** — once all singletons are ready, drain the
   pending list into `HandlerRegistry`:
   - command/query handlers map by `commandClass.getSimpleName()`,
   - event consumers append to the per-event list with their criticality
     (`@Critical` present → `CRITICAL` + the declared `order`, otherwise
     `NON_CRITICAL`),
   - pipeline behaviors are sorted by priority and stored in the orchestrator.
3. Log the totals (`{X commands, Y queries, Z events, W behaviors}`).

After step 3, the registry is **read-only at dispatch time**. Bean lifecycle
is unchanged — handlers stay Spring-managed (still get DI, profiles, AOP, etc.).

---

## 4. Handler registry

`HandlerRegistry` is a typed lookup index, not a container.

| Bucket | Map / list | Lookup |
| --- | --- | --- |
| Commands | `Map<String, ICommandHandler<?>>` keyed by `commandClass.getSimpleName()` | `getCommandHandler(name)` returns null if missing → buses throw `HandlerNotFoundException`. |
| Queries | `Map<String, IQueryHandler<?, ?>>` | same |
| Events | `Map<String, List<RegisteredEventConsumer>>` | `getEventConsumers(name)` returns empty list when no consumers — `EventBus` short-circuits. |
| System consumers | ordered `List<SystemConsumer>` | iterated synchronously before user consumers. Used by the event-store persistence consumer. |

> **Why simple-name keys?** Keeps lookup cheap and avoids classloader problems
> in tests. Trade-off: two classes named `OpenAccountCommand` in different packages
> will collide. The registrar logs a warning when it overrides a key.

The registry exposes introspection:

- `getRegisteredCommands() / getRegisteredCommandsDetailed()` (handler names),
- `getRegisteredQueries() / getRegisteredQueriesDetailed()`,
- `getRegisteredEvents()`,
- `getRegisteredBehaviors()` (delegated to `PipelineOrchestrator`).

---

## 5. Pipeline orchestrator

`PipelineOrchestrator` builds the behavior chain on each dispatch.

```java
TResponse execute(TRequest request, BehaviorScope scope, Supplier<TResponse> handler) {
    Set<Class<?>> skip = readSkipBehaviorAnnotation(request.getClass());
    var applicable = behaviors.stream()
        .filter(b -> b.scope == ALL || b.scope == scope)
        .filter(b -> !skip.contains(b.getInstance().getClass()))
        .filter(b -> b.requestType == null || b.requestType.isInstance(request))
        .toList();

    Supplier<TResponse> chain = handler;
    for (int i = applicable.size() - 1; i >= 0; i--) {
        var behavior = applicable.get(i);
        Supplier<TResponse> next = chain;
        chain = () -> behavior.handle(request, next);
    }
    return chain.get();
}
```

- behaviors are **sorted by priority ascending** at registration time.
- chain is built **right-to-left**, so behavior 0 is the outermost wrapper.
- per-request opt-out via `@SkipBehavior({ … })`.
- per-request type filtering is supported (`RegisteredBehavior.requestType`); built-ins
  use `requestType = null` (apply to everything in the scope).

Built-in priorities are spaced for headroom:

| Behavior | Priority | Outermost? |
| --- | --- | --- |
| `ExceptionHandlingBehavior` | `-100` | yes — wraps everything else |
| `LoggingBehavior` | `0` | |
| `PerformanceBehavior` | `10` | |
| `ValidationBehavior` | `100` | innermost — fails fast before the handler |

User behaviors at priority 50 sit between Performance and Validation.

---

## 6. Command bus

`CommandBus.send(command)`:

1. Resolve handler by `command.getClass().getSimpleName()`. Throw
   `HandlerNotFoundException` if missing.
2. Emit a `COMMAND_DISPATCHED` step (if the flow emitter is on).
3. Set `MediatorContext.currentHandlerName`.
4. Hand off to `PipelineOrchestrator.execute(command, COMMAND, () -> handler.execute(command))`.
5. Around the handler call, emit `COMMAND_HANDLER_STARTED / COMPLETED / FAILED`.
6. Return `void` (commands are side-effect-only).

The whole call is wrapped by `MediatorContextManager.runWithNewContext` at the
`MediatorBus` level, so a fresh correlation id is created per top-level send.

---

## 7. Query bus

Same shape as `CommandBus`, except the handler returns `R`. Step types are
`QUERY_DISPATCHED / QUERY_HANDLER_STARTED / QUERY_HANDLER_COMPLETED / QUERY_HANDLER_FAILED`.

---

## 8. Event bus — three-phase dispatch with compensation

`EventBus.publish(event)` is the most interesting machine.

```
publish(event)
 ├─ generate eventId (UUID)
 ├─ emit EVENT_PUBLISHED
 │
 ├─ Phase 1 — system consumers (sync, in registration order)
 │    for each SystemConsumer:
 │      runWithCausation(eventId, () -> sys.handle(event))
 │      emit SYSTEM_CONSUMER_*
 │
 ├─ split user consumers:
 │    critical    = consumers @Critical, sorted by order asc
 │    nonCritical = the rest
 │
 ├─ Phase 2 — critical consumers (sync, ordered)
 │    succeeded = []
 │    for each c in critical:
 │      try:
 │        runWithCausation(eventId, () -> c.handle(event))
 │        succeeded += c
 │      catch e:
 │        # compensate previously-succeeded ones in REVERSE order
 │        for i = succeeded.size-1 .. 0:
 │          if c instanceof ICriticalEventConsumer:
 │            ev = c.applyCompensatingEvent(event)
 │            if ev != null: publish(ev)        ← recursive — also stored to event store
 │            else:           c.compensate(event)
 │        rethrow
 │
 └─ Phase 3 — non-critical consumers (async, fire-and-forget)
      for each c in nonCritical:
        asyncExecutor.execute(() -> c.handle(event))   ← virtual threads by default
```

Key properties:

- **Phase 1 always runs first.** This is how the event store gets the event
  *before* any user side effect, so an audit row exists even if a critical
  consumer fails.
- **Critical phase is synchronous and order-deterministic.** This is the saga
  control plane.
- **Compensation fires in reverse order** — the most-recently-succeeded
  consumer is undone first.
- **Compensating events are recursively published.** They go through the same
  `EventBus`, so they're persisted, traced, and can themselves have consumers.
- **Non-critical consumers never block the publisher.** Failures are logged but
  don't propagate.
- The default async executor is a virtual-thread `SimpleAsyncTaskExecutor`
  (`mediatorAsyncExecutor` bean). Override by declaring your own
  `AsyncTaskExecutor` bean with that name.

Return value is an `EventPublishResult(totalHandlers, criticalSucceeded,
nonCriticalDispatched, compensationsRun)`.

---

## 9. Mediator context (correlation / causation)

```java
class MediatorContextManager {
    private final InheritableThreadLocal<MediatorContext> storage = new InheritableThreadLocal<>();
    public <T> T runWithNewContext(Supplier<T> cb)        { /* push fresh context */ }
    public <T> T runWithCausation(String eventId, Supplier<T> cb) { /* set causationId */ }
    public boolean hasContext() { return storage.get() != null; }
    public MediatorContext getContext() { /* lazy-init */ }
}
```

`MediatorContext` carries:

- `correlationId` — random UUID, scoped to one top-level dispatch.
- `causationId` — the id of the event that triggered the current consumer.
- `currentEventId` — same as causationId (kept for clarity).
- `currentHandlerName` — set by the buses just before pipeline execution.

`InheritableThreadLocal` propagates context to threads spawned during the call,
which matters for non-critical consumers running on the async executor.

`MediatorBus.publish` is special — if a context already exists (we're being
called from inside another consumer), it **reuses** it; otherwise it opens a new
one. This keeps the correlation id stable across an entire causal chain.

---

## 10. Event store

The event store is opt-in (`mediator.event-store.enabled=true`) and has two modes:

- **`audit`** — every published event is INSERTed without sequencing.
- **`source`** — used together with `AggregateRepository`. The persistence
  consumer also assigns a monotonically increasing `sequence_number` per
  `(aggregate_type, aggregate_id)` (read via `repository.getNextSequence(...)`).

### Schema (Postgres)

```sql
CREATE TABLE IF NOT EXISTS <table_name> (
  event_id        VARCHAR(36) PRIMARY KEY,
  event_type      VARCHAR(255) NOT NULL,
  payload         JSONB NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stored_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  correlation_id  VARCHAR(36),
  causation_id    VARCHAR(36),
  metadata        JSONB DEFAULT '{}',
  aggregate_type  VARCHAR(255),
  aggregate_id    VARCHAR(255),
  sequence_number BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_<t>_aggregate_sequence
       ON <t> (aggregate_type, aggregate_id, sequence_number)
       WHERE sequence_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_<t>_aggregate     ON <t>(aggregate_type, aggregate_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_<t>_type          ON <t>(event_type);
CREATE INDEX IF NOT EXISTS idx_<t>_correlation   ON <t>(correlation_id);
CREATE INDEX IF NOT EXISTS idx_<t>_occurred      ON <t>(occurred_at);
```

`EventStoreSchemaManager` runs once on startup (`@Bean(initMethod = "initialize")`).
Disable with `auto-create-schema: false` to manage the schema yourself (Flyway, etc.).

### Repositories

Both `JpaEventStoreRepository` and `JdbcEventStoreRepository` implement the same
SPI:

```java
interface IEventStoreRepository {
    void saveEvent(StoredEvent event);
    void appendEvents(String aggregateType, String aggregateId,
                      List<StoredEvent> events, long expectedVersion);   // throws ConcurrencyException
    List<StoredEvent> getEventsForAggregate(String aggregateType, String aggregateId);
    long getNextSequence(String aggregateType, String aggregateId);
}
```

Both serialize `payload` and `metadata` as JSONB (`?::jsonb` cast). The JPA flavor
uses native queries against whatever `EntityManager` the host app provides — there
is no `@Entity` scanning, so it doesn't interfere with the user's persistence
context.

### Persistence consumer

`EventStorePersistenceConsumer` is a `IEventConsumer<IEvent>` registered as a
**system** consumer. Per-event:

1. Build `StoredEvent` from the event's reflected fields.
2. Read `correlationId` / `causationId` from the current `MediatorContext`.
3. If the event class is annotated with `@DomainEvent(aggregateType, aggregateIdField)`,
   reflectively read the id field and set `aggregateType` + `aggregateId`.
4. In `source` mode with an `aggregateInfo`, also call `getNextSequence(...)` and
   assign `sequence_number`.
5. `repository.saveEvent(stored)`.

> If a consumer further down the chain mutates the event (don't do this), the
> persisted payload reflects whatever the event looks like at persist time.
> Treat events as immutable.

---

## 11. Aggregate root and aggregate repository

`AggregateRoot<TId>` is an abstract class that buffers uncommitted events and
replays them by reflection.

```java
public abstract class AggregateRoot<TId> {
    private final List<IEvent> uncommittedEvents = new ArrayList<>();
    private long version = 0;

    public abstract TId    getId();
    public abstract String getAggregateType();

    public void loadFromHistory(List<IEvent> events) { events.forEach(e -> applyEvent(e, false)); }

    protected void apply(IEvent event)               { applyEvent(event, true); }

    private void applyEvent(IEvent event, boolean isNew) {
        // call this.applyXxxEvent(XxxEvent) by reflection
        Method m = getClass().getDeclaredMethod("apply" + event.getClass().getSimpleName(),
                                                event.getClass());
        m.setAccessible(true);
        m.invoke(this, event);
        if (isNew) uncommittedEvents.add(event);
        version++;
    }
}
```

- The `applyXxxEvent(XxxEvent)` convention is intentional — keeps state mutation
  separate from validation logic.
- A missing `applyXxxEvent` method is logged as a warning, not a crash.

`AggregateRepository<T, TId>`:

- `findById(id)` reads the event stream from `IEventStoreRepository.getEventsForAggregate(...)`,
  deserializes via the user-provided `deserializeEvent(...)`, and replays them.
- `save(aggregate)` publishes each uncommitted event through the **mediator bus**
  (so the persistence consumer writes them and downstream consumers run), then
  clears the buffer.
- The `@ForAggregate(BankAccount.class)` annotation is currently informational —
  it's there for future reflection-based wiring (for example, to wire a default
  repository per aggregate without a subclass).

> Note: the current `save` flow does **not** use `appendEvents(... expectedVersion)`
> — there's no optimistic-concurrency check end-to-end. `ConcurrencyException` is
> only thrown if you call `appendEvents` directly. See the roadmap.

---

## 12. Built-in pipeline behaviors

| Behavior | Priority | Property | What it does |
| --- | --- | --- | --- |
| `ExceptionHandlingBehavior` | -100 | `mediator.behaviors.exception-handling-enabled` | Catches and logs, then rethrows. Outermost so it sees everything else's exceptions. |
| `LoggingBehavior` | 0 | `mediator.behaviors.logging-enabled` | Logs `Handling X…` / `Handled X in Yms` / `Failed handling X after Yms`. |
| `PerformanceBehavior` | 10 | `mediator.behaviors.performance-tracking-enabled` (+ `performance-threshold-ms`) | WARN log when `elapsed > thresholdMs`. |
| `ValidationBehavior` | 100 | `mediator.behaviors.validation-enabled` | Calls `jakarta.validation.Validator.validate(request)`; throws `ValidationException(List<ValidationError>)` if non-empty. |

---

## 13. Auto-configuration matrix

`spring-mediator-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

| Auto-configuration | Triggers | Provides | Notes |
| --- | --- | --- | --- |
| `MediatorAutoConfiguration` | always | `MediatorContextManager`, `PipelineOrchestrator`, `HandlerRegistry`, `CommandBus`, `QueryBus`, `EventBus`, `MediatorBus`, `mediatorAsyncExecutor`, `MediatorHandlerRegistrar` | every bean is `@ConditionalOnMissingBean` — override anything by declaring your own. |
| `MediatorBehaviorAutoConfiguration` | runs after `MediatorAutoConfiguration` | the four built-in behaviors gated by `mediator.behaviors.*-enabled` | Each behavior bean **also** registers itself with `HandlerRegistry.registerPipelineBehavior(...)` at construction time. |
| `MediatorEventStoreAutoConfiguration` | `mediator.event-store.enabled=true` | `EventStorePersistenceConsumer` (registers with the registry as a system consumer) + `EventStoreSchemaManager` (only if `DataSource` is present and `auto-create-schema=true|missing`) | |
| `MediatorEventStoreJpaAutoConfiguration` | event store enabled + `jakarta.persistence.EntityManager` on classpath, no other `IEventStoreRepository` defined, `mediator.event-store.strategy=jpa` (default) | `JpaEventStoreRepository` | |
| `MediatorEventStoreJdbcAutoConfiguration` | event store enabled + `JdbcTemplate` on classpath | `JdbcEventStoreRepository` (with `mediator.event-store.strategy=jdbc`, **or** as fallback when JPA isn't on the classpath). May provision a dedicated `HikariDataSource` named `mediator-event-store` if `use-existing-datasource=false`. | |
| `MediatorFlowAutoConfiguration` | `mediator.flow.enabled=true` | `StepEmitter` + `TopologyCollector` | TopologyCollector posts on startup with retry/backoff (max 10 attempts, capped at 60s). |

---

## 14. Flow tracing protocol

When `mediator.flow.enabled=true`:

- `TopologyCollector.start()` runs once, ~5s after startup. It posts the registered
  topology to `<collector-url>/collect/topology`. Retries on non-2xx with exponential
  backoff (5s, 10s, 20s, …, capped at 60s) up to 10 attempts.
- `StepEmitter.emit(type, name[, error])` is called from inside the buses on every
  step. Steps are buffered (max 1000) and flushed in batches of `batch-size`
  (default 50) every `flush-interval-ms` (default 2000) via async HTTP POST to
  `<collector-url>/collect/steps`. `httpClient.sendAsync(...)` — failures are
  silently dropped (debug-logged).

Step-type enum (in `StepEmitter.StepType`): `COMMAND_DISPATCHED`,
`COMMAND_HANDLER_STARTED/COMPLETED/FAILED`, `QUERY_DISPATCHED`,
`QUERY_HANDLER_STARTED/COMPLETED/FAILED`, `EVENT_PUBLISHED`,
`COMPENSATING_EVENT_PUBLISHED`, `SYSTEM_CONSUMER_STARTED/COMPLETED/FAILED`,
`CRITICAL_CONSUMER_STARTED/COMPLETED/FAILED`,
`NONCRITICAL_CONSUMER_DISPATCHED/COMPLETED/FAILED`,
`BEHAVIOR_ENTERED/COMPLETED/FAILED`, `RETRY_ATTEMPTED`,
`COMPENSATION_STARTED/COMPLETED/FAILED`.

The collector is intentionally out-of-process and language-agnostic.

---

## 15. Known limitations

| # | Limitation | Where | Path forward |
| --- | --- | --- | --- |
| 1 | Handler keys are simple-class-names, not FQN. | `HandlerRegistry` | Switch to FQN; register both for backward compat. |
| 2 | `BehaviorScope` only has COMMAND/QUERY/ALL — no per-event behaviors. | `BehaviorScope`, `EventBus` | Add an EVENT scope; route events through the orchestrator. |
| 3 | `AggregateRepository.save` doesn't enforce optimistic concurrency. | `AggregateRepository` | Switch from publish-each-event to `appendEvents(... expectedVersion)`. |
| 4 | Retry isn't a built-in behavior, but the step type `RETRY_ATTEMPTED` is reserved. | core/behaviors | Add a `RetryBehavior` with backoff + circuit breaker. |
| 5 | The flow emitter loses steps if the collector is unreachable past the 1000-step buffer. | `StepEmitter` | Optional disk spool or backpressure. |
| 6 | Event store payload uses Jackson, but no Jackson Module config is exposed. | `EventStorePersistenceConsumer` | Inject `ObjectMapper` (use the app's bean), allow custom modules. |
| 7 | No outbox pattern — events are published in-process without a transactional handoff to Kafka/etc. | EventBus + event store | Add an outbox-poller with at-least-once delivery. |
| 8 | `JpaEventStoreRepository.appendEvents` reads-then-writes; collision window. | `JpaEventStoreRepository` | Use `INSERT … ON CONFLICT` or a row-locked sequence table. |

For a forward-looking plan, see [ROADMAP.md](./ROADMAP.md).
