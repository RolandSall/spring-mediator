# Roadmap — what to build next

Spring Mediator today covers the core mediator loop: commands, queries, events with
critical/non-critical consumers, compensation, pipeline middleware, optional event
store (audit + event sourcing), correlation/causation context, and language-agnostic
flow tracing. This document is a discussion of **what could come next**, with rough
sizing and trade-offs so the next conversation can pick the right targets.

> Each item below is shaped as: **What → Why → Sketch → Risk/Trade-off**. They
> aren't prioritized as a sequence — pick by what your team actually needs.

---

## Tier 1 — fills concrete gaps in current functionality

### 1.1 Optimistic concurrency end-to-end on event-sourced aggregates

**What.** `AggregateRepository.save(...)` currently publishes each uncommitted event
through the bus. The persistence consumer just `INSERT`s with the next
sequence. Two writers loading the aggregate at version `V` and saving in parallel
both compute `V+1` for their first event — depending on the unique index they
either both succeed (audit gap) or one fails with a generic SQL exception.

**Why.** Optimistic concurrency is the contract event sourcing requires; we already
have `ConcurrencyException` and `IEventStoreRepository.appendEvents(...,
expectedVersion)` — they're just unused.

**Sketch.** Have `save(aggregate)` call `appendEvents(type, id, events,
aggregate.getVersion() - events.size())` instead of publishing per-event. Then a
separate "outbox" pass publishes the saved events through the bus. Throws
`ConcurrencyException` cleanly on conflict.

**Trade-off.** Two-step write (DB then bus) makes failure modes more interesting —
needs decision: are events that are persisted-but-not-published replayable on
startup? Probably yes via an outbox table, see 1.4.

---

### 1.2 First-class retry behavior

**What.** A built-in `RetryBehavior` with exponential backoff and a max-attempt
ceiling. The `RETRY_ATTEMPTED` step type already exists in `StepEmitter.StepType`
but is unused.

**Why.** Today every handler that touches an external system has to roll its own
retry. A pipeline behavior centralizes the policy and emits trace events.

**Sketch.**

```java
@PipelineBehavior(priority = -50, scope = ALL)
public class RetryBehavior implements IPipelineBehavior<Object, Object> {
    private final RetryPolicy policy;          // attempts, backoff, retryable predicate
    public Object handle(Object req, Supplier<Object> next) {
        for (int attempt = 1; ; attempt++) {
            try { return next.get(); }
            catch (Exception e) {
                if (!policy.shouldRetry(e, attempt)) throw e;
                emitter.emit(RETRY_ATTEMPTED, req.getClass().getSimpleName(), e);
                sleep(policy.delay(attempt));
            }
        }
    }
}
```

Honor `@Retryable(attempts = 3, backoff = 100)` annotation on commands/queries to
opt-in per-request.

**Trade-off.** Retries on commands can double-write. Pair with idempotency keys
(see 1.3).

---

### 1.3 Idempotency keys on commands

**What.** A cross-cutting `IdempotencyBehavior` that hashes (commandClass +
explicit `IdempotencyKey` field) and short-circuits if the same key was processed
recently. Backed by a small dedicated table or Redis.

**Why.** Without it, retried HTTP requests, message redelivery, and the retry
behavior all risk double execution.

**Sketch.** New annotation `@Idempotent(field = "requestId", ttl = "PT24H")` plus
behavior at priority −20 (after retry, before validation).

**Trade-off.** Requires a store; doesn't solve "same effect from logically
different commands" — that's the user's job.

---

### 1.4 Transactional outbox for events

**What.** When `event-store.enabled=true`, write events to the existing
`domain_events` table inside the same DB transaction as the state change, then a
poller publishes unprocessed rows to downstream subscribers (Kafka, RabbitMQ,
HTTP, …).

**Why.** Today the event store is purely an append-only log — there's no guaranteed
hand-off to another system. Outbox is the standard answer for "exactly-once across
process boundaries".

**Sketch.** Add a `published_at TIMESTAMP NULL` column. A scheduled poller
(`@Scheduled` or via a Postgres `LISTEN/NOTIFY` adapter) reads `WHERE published_at
IS NULL ORDER BY stored_at LIMIT N`, publishes via a pluggable
`OutboxDispatcher` (Kafka, NATS, etc.), then sets `published_at`.

**Trade-off.** Polling adds latency; LISTEN/NOTIFY ties you to Postgres. Make
dispatcher pluggable so adopters swap freely.

---

### 1.5 Snapshotting for long-lived aggregates

**What.** Replaying 100k events on every load is fine until it isn't. Add a
sibling `aggregate_snapshots` table; `AggregateRepository.findById` first reads
the latest snapshot, then events with `sequence_number > snapshot.sequence`.

**Why.** Bound replay cost.

**Sketch.** New `ISnapshotStrategy`: `boolean shouldSnapshot(aggregate)`, plus
JSON `snapshotState` / `restoreFromSnapshot` hooks on `AggregateRoot`. Fire
on `save()` when policy returns true.

**Trade-off.** Snapshots are versioned — schema evolution gets a second axis.
Provide a "rebuild from events" CLI for cases where snapshots become invalid.

---

### 1.6 Streaming queries (server-sent events from bus + event store)

**What.** `mediator.queryStream(query)` returning `Flux<R>` (Reactor) or
`Stream<R>` for read-models that should be pushed.

**Why.** Today everything is request-response. A subscription model fits live
dashboards, projections, audit-log tailing.

**Sketch.** New marker `IStreamingQuery<R>` + `IStreamingQueryHandler<T, R>`.
Wire reactor as `compileOnly` on core; only available if reactor is on the user's
classpath.

**Trade-off.** Requires reactor or kotlin coroutines opt-in; doubles the surface
area. Only worth it if a real consumer wants it.

---

## Tier 2 — observability / ops

### 2.1 OpenTelemetry adapter

**What.** Map the existing `StepEmitter.StepType` events to OTel spans and
metrics. `COMMAND_DISPATCHED` opens a span, `COMMAND_HANDLER_COMPLETED` closes
it, `CRITICAL_CONSUMER_FAILED` records an exception event, etc.

**Why.** Ship a span tree per command/event chain into Tempo/Jaeger/Honeycomb
without writing custom collectors.

**Sketch.** New module `spring-mediator-otel`: an `IStepListener` SPI sits next
to `StepEmitter`; the OTel listener mirrors steps to spans using
`io.opentelemetry.api.trace.Tracer`. Correlation id → trace id mapping for free.

**Trade-off.** Adds an SPI; the existing flow-collector format becomes one
listener among several.

---

### 2.2 Micrometer counters / timers

**What.** `mediator.commands.handled{name=…, outcome=success|failure}` counter,
`…handler.duration` timer, `mediator.events.compensations` counter,
`mediator.events.consumers.dispatched{criticality=…}`, etc.

**Why.** Standard production metrics with zero user code. Already half-done by
the existing `LoggingBehavior` + `PerformanceBehavior` — just register meters
instead of (or alongside) logging.

**Trade-off.** Add Micrometer as `compileOnly` — auto-config kicks in only when
on the classpath.

---

### 2.3 Structured logging with MDC injection

**What.** A built-in MDC-injecting behavior that puts `correlationId`,
`causationId`, `handlerName` into the SLF4J MDC for every log line inside the
handler.

**Why.** Recipe 11 in the functional doc shows users doing this manually. Bake it
in.

**Trade-off.** Behavior must be careful to clear MDC on exit; one bug means
correlation ids leak across requests.

---

## Tier 3 — protocol adapters / integrations

### 3.1 Kafka adapter

**What.** A `KafkaEventConsumer<T>` that maps a Kafka topic record to an
`IEvent` and republishes through `MediatorBus.publish`. Plus a
`KafkaOutboxDispatcher` for 1.4.

**Why.** Big enterprises prefer Kafka over polling. Same SPI shape as
`OutboxDispatcher`.

**Sketch.** New module `spring-mediator-kafka`. Wraps `spring-kafka`;
`@KafkaConsumer(topic = "orders.v1", deserializer = …)` annotation plus a
`Map<String, Class<? extends IEvent>>` registry.

**Trade-off.** Requires Kafka dependency; user opts in via separate starter.

---

### 3.2 HTTP / gRPC inbound adapter

**What.** Auto-generate REST endpoints for commands annotated with
`@HttpCommand("/orders" )` so users don't have to hand-write controllers
that just `mediator.send(...)`.

**Why.** Reduce boilerplate in `OrderController`-style classes (see
`example-audit/.../OrderController.java`).

**Sketch.** New module `spring-mediator-web`: a `RouterFunction` populated from
the registry at startup. Annotation drives method, path, status code, error
mapping.

**Trade-off.** Auto-generated endpoints are great until they aren't (validation,
auth filters, OpenAPI groups, …). Make it strictly opt-in per command.

---

### 3.3 Server-side cron / scheduled commands

**What.** `@Scheduled(cron = "…")` on a command class to dispatch it via the
mediator on a schedule. Works just like `@Scheduled` on a method but flows
through the bus (so all behaviors apply).

**Trade-off.** Trivial to implement; mostly a discoverability win.

---

## Tier 4 — testing & DX

### 4.1 `MediatorTestKit`

**What.** A `@MediatorTest` JUnit annotation that:

- spins up the bus + registry without Spring Boot,
- captures published events into a list (`assertPublished(OrderPlacedEvent.class)`),
- mocks any handler (`given(GetOrderQuery.class).returns(...)`),
- asserts compensation (`assertCompensated(InventoryReleasedEvent.class)`).

**Why.** Today, integration testing the saga path means full Spring + Postgres
or hand-rolling test doubles.

**Sketch.** New module `spring-mediator-test`. Register a `MediatorBus` backed
by a `RecordingHandlerRegistry`.

**Trade-off.** Testkits drift from the real container — keep this as a
*supplement* to integration tests, not a replacement.

---

### 4.2 Detect missing handlers at startup

**What.** Optional flag `mediator.startup.fail-on-orphan-handlers=true` that
fails the boot if a `@CommandHandler(X)` exists but no `X.class implements
ICommand`, or vice versa, or if two handlers register for the same key.

**Why.** Today these surface as warnings (`Overriding existing command handler for X`)
or runtime `HandlerNotFoundException`. Catching at boot saves hours.

**Trade-off.** None really — pure win. Just needs implementation.

---

### 4.3 IDE-friendly registration metadata

**What.** Generate a tiny `META-INF/spring-mediator-registry.json` at build time
listing every registered command/query/event/behavior class so IDEs can show
"who handles this?" without runtime introspection.

**Sketch.** Annotation processor that mirrors the same logic as
`MediatorHandlerRegistrar`.

**Trade-off.** Adds a build-time step. Nice-to-have, not blocking anyone.

---

## Tier 5 — language / DDD niceties

### 5.1 Kotlin coroutines support

**What.** `suspend` versions of `send/query/publish` and a coroutine-aware
context propagation (current `InheritableThreadLocal` doesn't survive a thread
hop).

**Trade-off.** Pulls Kotlin runtime as `compileOnly`; users on plain Java pay
nothing.

---

### 5.2 jMolecules / DDD building-block alignment

**What.** Recognize `@org.jmolecules.architecture.cqrs.Command` /
`@AggregateRoot` annotations as equivalents to the in-house ones.

**Why.** Plays nice with Spring Modulith, Moduliths, and DDD-leaning shops.

**Trade-off.** Optional dependency, low cost.

---

### 5.3 Native image support

**What.** Provide GraalVM `native-image` reflection hints (currently
`AggregateRoot.applyXxxEvent`, `EventStorePersistenceConsumer.field` reflection,
and the `@DomainEvent` field-name lookup will all break under AOT).

**Sketch.** Add a `RuntimeHintsRegistrar` in autoconfigure that registers the
relevant classes/methods.

**Trade-off.** A bit fiddly to test; needs CI on a native build.

---

## Cross-cutting decisions to settle

These shape several items above:

1. **One Spring Boot version target, or compatibility window?** Currently pinned
   to 3.4.3. Pick: track latest, or test against 3.2 / 3.3 / 3.4 / 3.5.
2. **Which databases do we officially support for the event store?** Today the
   SQL is Postgres-specific (`JSONB`, `?::jsonb` casts). Either embrace Postgres,
   or abstract via a `Dialect` SPI for MySQL/MSSQL/H2.
3. **Module proliferation policy.** Items above suggest `spring-mediator-otel`,
   `-kafka`, `-web`, `-test`. Decide whether these are submodules of this repo
   or separate repos.
4. **Versioning + compatibility.** Until 1.0, breaking changes are OK. Once 1.0
   ships, behaviors / SPIs become a public contract. The publishing pipeline is
   already in place — agree on a release cadence.

---

## Suggested first three milestones (if you want a pick)

1. **Milestone A — make event sourcing actually safe.** 1.1 (optimistic concurrency)
   + 1.5 (snapshotting) + 4.2 (boot-time validation). One sprint, big confidence
   bump.
2. **Milestone B — production observability.** 2.1 (OTel) + 2.2 (Micrometer) +
   2.3 (MDC). Pulls the library into the standard Java prod stack.
3. **Milestone C — at-least-once delivery.** 1.4 (outbox) + 1.2 (retry) + 1.3
   (idempotency keys) + 3.1 (Kafka adapter). Turns the library into a viable
   alternative to a message-broker SDK for in-process services.

Pick one. Each can ship as a `1.x` minor without breaking the public surface.
