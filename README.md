# Spring Mediator

An application messaging fabric for Spring Boot 3 (Java 21). One starter gives you:

- **CQRS** — commands, queries, and a single `MediatorBus` entry point;
- **Domain events** with multiple consumers per event;
- **Critical / non-critical consumers** with **automatic compensation** in reverse
  order on failure (saga-light, no separate engine);
- **Pipeline behaviors** — middleware around commands and queries (validation,
  logging, performance, exception handling, plus your own);
- **Optional event store** — audit mode, or full **event sourcing** via
  `AggregateRoot` / `AggregateRepository` with sequence numbers per aggregate;
- **Correlation + causation** propagated across the whole flow via an
  `InheritableThreadLocal` context;
- **Language-agnostic flow tracing** — emits topology + per-step events to any
  HTTP collector you write.

Inspired by MediatR (.NET) and nest-mediator, but built for Spring's bean container.

- **Group:** `io.github.springmediator`
- **Version:** `1.0.0-SNAPSHOT`
- **Spring Boot:** 3.4.3
- **Java:** 21

Build status: `./gradlew clean build` → BUILD SUCCESSFUL.

### Documentation

- [`docs/FUNCTIONAL.md`](docs/FUNCTIONAL.md) — task-oriented usage guide with recipes.
- [`docs/TECHNICAL.md`](docs/TECHNICAL.md) — internals, class-by-class, dispatch flow.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — proposed future enhancements with sizing.

---

## Table of contents

1. [How it works](#how-it-works)
2. [Modules](#modules)
3. [Quick start](#quick-start)
4. [Core concepts](#core-concepts)
   - [Commands](#commands)
   - [Queries](#queries)
   - [Events](#events)
   - [Critical vs. non-critical consumers and compensation](#critical-vs-non-critical-consumers-and-compensation)
   - [Pipeline behaviors](#pipeline-behaviors)
   - [Built-in behaviors](#built-in-behaviors)
   - [Skipping behaviors per request](#skipping-behaviors-per-request)
   - [Mediator context (correlation / causation)](#mediator-context-correlation--causation)
5. [Event store](#event-store)
   - [Audit mode](#audit-mode)
   - [Source mode (event sourcing)](#source-mode-event-sourcing)
   - [Aggregate root and repository](#aggregate-root-and-repository)
6. [Flow tracing](#flow-tracing)
7. [Configuration reference](#configuration-reference)
8. [Examples](#examples)
9. [Publishing pipeline (Maven Central / GitHub Packages)](#publishing-pipeline)
10. [Consuming the library](#consuming-the-library)

---

## How it works

`MediatorBus` is the single entry point an application uses to send commands, run
queries, and publish events. Internally, three buses fan out the work:

```
                 ┌───────────────────────────────┐
                 │          MediatorBus          │
                 │  (facade + context manager)   │
                 └───┬─────────────┬─────────────┘
                     │             │             │
                send │       query │     publish │
                     ▼             ▼             ▼
              CommandBus      QueryBus       EventBus
                     │             │             │
                     ▼             ▼             ▼
       PipelineOrchestrator (behaviors chain)    │
                     │             │             │
                     ▼             ▼             ▼
              ICommandHandler  IQueryHandler  N × IEventConsumer
                                                (system → critical → non-critical)
```

Bean discovery is done once at startup by `MediatorHandlerRegistrar`
(a `BeanPostProcessor` + `SmartInitializingSingleton`):

- Beans annotated with `@CommandHandler`, `@QueryHandler`, `@EventHandler`, and
  `@PipelineBehavior` are detected and indexed in `HandlerRegistry`.
- Handler instances stay Spring-managed; `HandlerRegistry` is just a lookup index.
- Once `afterSingletonsInstantiated()` fires, the registry is sealed and ready.

At dispatch time:

1. `MediatorBus.send/query/publish` opens a `MediatorContext` (correlationId, causationId)
   using `InheritableThreadLocal`, so context flows to child threads.
2. `CommandBus`/`QueryBus` look up the handler, then build a behavior chain
   in priority order and execute it; the terminal step is the handler.
3. `EventBus` runs three phases for each published event:
   - **System consumers** (e.g. event-store persistence) — synchronous, in registration order.
   - **Critical consumers** — synchronous, sorted by `@Critical(order = ...)`.
     If one fails, **previously succeeded critical consumers are compensated in
     reverse order** (either by publishing a compensating event or by calling
     `compensate(event)` directly).
   - **Non-critical consumers** — fire-and-forget on the `mediatorAsyncExecutor`
     (virtual-thread `SimpleAsyncTaskExecutor` by default). Failures are logged.

If `mediator.event-store.enabled=true`, an `EventStorePersistenceConsumer` is registered
as a system consumer so every published event is written to a JSONB-typed Postgres
table before user consumers run.

If `mediator.flow.enabled=true`, a `StepEmitter` records each step
(`COMMAND_DISPATCHED`, `CRITICAL_CONSUMER_FAILED`, `COMPENSATION_STARTED`, …) and
batches them to the optional Mediator Flow Server, which also receives the service
topology at startup.

---

## Modules

The build is a Gradle multi-module project (`settings.gradle.kts`):

| Module | Artifact | Purpose | Depends on |
| --- | --- | --- | --- |
| `spring-mediator-core` | `io.github.springmediator:spring-mediator-core` | Pure Java/Spring-context library: marker interfaces, annotations, buses, pipeline orchestrator, built-in behaviors, context manager, event store SPI + JDBC/JPA implementations, aggregate base classes, flow primitives. No Spring Boot dependency. | `spring-context`, optional `spring-jdbc` / `jakarta.persistence-api` / `jakarta.validation-api` / `jackson-databind` (all `compileOnly`). |
| `spring-mediator-autoconfigure` | `io.github.springmediator:spring-mediator-autoconfigure` | Spring Boot auto-configurations + `MediatorHandlerRegistrar` + `MediatorProperties`. Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. | `core`, `spring-boot-autoconfigure`, optional JDBC/JPA/validation/Hikari (`compileOnly`). |
| `spring-mediator-starter` | `io.github.springmediator:spring-mediator-starter` | The thing applications depend on. Empty by design (Spring Boot starter convention) — pulls in `core` + `autoconfigure` and nothing else, so transitive bloat stays minimal. | `core`, `autoconfigure`. |
| `example-audit` | (not published) | Sample Spring Boot service that uses Spring Mediator with the **audit** event-store mode. Also demonstrates a custom pipeline behavior and a critical consumer with compensation. | `starter`, `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, postgres driver. |
| `example-source` | (not published) | Sample service that uses the **event-sourcing** mode with `AggregateRoot` / `AggregateRepository` (`BankAccount`). | same as `example-audit`. |

The starter is the recommended dependency for application code. The core jar can be
used standalone if you want to wire beans manually (no Spring Boot).

### What auto-configurations are registered

`spring-mediator-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.github.springmediator.mediator.autoconfigure.MediatorAutoConfiguration
io.github.springmediator.mediator.autoconfigure.MediatorBehaviorAutoConfiguration
io.github.springmediator.mediator.autoconfigure.MediatorEventStoreAutoConfiguration
io.github.springmediator.mediator.autoconfigure.MediatorEventStoreJpaAutoConfiguration
io.github.springmediator.mediator.autoconfigure.MediatorEventStoreJdbcAutoConfiguration
io.github.springmediator.mediator.autoconfigure.MediatorFlowAutoConfiguration
```

| Auto-configuration | Triggers when | Provides |
| --- | --- | --- |
| `MediatorAutoConfiguration` | always | `MediatorContextManager`, `PipelineOrchestrator`, `HandlerRegistry`, `CommandBus`, `QueryBus`, `EventBus`, `MediatorBus`, `mediatorAsyncExecutor` (virtual-threaded `SimpleAsyncTaskExecutor`), and the `MediatorHandlerRegistrar` `BeanPostProcessor`. |
| `MediatorBehaviorAutoConfiguration` | per `mediator.behaviors.*-enabled=true` flag | `LoggingBehavior` (priority 0), `ValidationBehavior` (priority 100, requires `jakarta.validation.Validator`), `ExceptionHandlingBehavior` (priority −100), `PerformanceBehavior` (priority 10). |
| `MediatorEventStoreAutoConfiguration` | `mediator.event-store.enabled=true` | `EventStorePersistenceConsumer` (system consumer) + `EventStoreSchemaManager` (creates the events table on startup). |
| `MediatorEventStoreJpaAutoConfiguration` | event store enabled + `jakarta.persistence.EntityManager` on classpath, no other `IEventStoreRepository` defined, `mediator.event-store.strategy=jpa` (default when JPA is present). | `JpaEventStoreRepository`. |
| `MediatorEventStoreJdbcAutoConfiguration` | event store enabled + `JdbcTemplate` on classpath. Optionally creates a dedicated `mediator-event-store` `HikariDataSource` if `use-existing-datasource=false`. | `JdbcEventStoreRepository` (used either with `strategy=jdbc` or as fallback when JPA isn't present). |
| `MediatorFlowAutoConfiguration` | `mediator.flow.enabled=true` | `StepEmitter` + `TopologyCollector` that publish steps + topology to the configured `collector-url`. |

Everything is `@ConditionalOnMissingBean` so applications can override any piece by
declaring their own bean.

---

## Quick start

Add the starter to your Spring Boot 3 application:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.springmediator:spring-mediator-starter:1.0.0")

    // Required for ValidationBehavior (only if you turn it on)
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Required if you enable the event store with JDBC
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
}
```

Enable whatever you need in `application.yml`:

```yaml
mediator:
  behaviors:
    logging-enabled: true
    validation-enabled: true
    exception-handling-enabled: true
    performance-tracking-enabled: true
    performance-threshold-ms: 100

  event-store:
    enabled: true
    mode: audit               # or "source"
    table-name: domain_events
    use-existing-datasource: true
    auto-create-schema: true

  flow:
    enabled: false
    collector-url: http://localhost:4800
    service-name: my-service
```

Inject `MediatorBus` anywhere and dispatch:

```java
@RestController
class OrderController {
    private final MediatorBus mediator;

    OrderController(MediatorBus mediator) { this.mediator = mediator; }

    @PostMapping("/orders")
    void create(@RequestBody CreateOrderRequest body) {
        mediator.send(new CreateOrderCommand(body.customerId(), body.items(), body.total()));
    }

    @GetMapping("/orders/{id}")
    Order get(@PathVariable String id) {
        return mediator.query(new GetOrderQuery(id));
    }
}
```

---

## Core concepts

### Commands

A command is intent to change state. One command, one handler, no return value.

```java
public class CreateOrderCommand implements ICommand {
    @NotBlank private final String customerId;
    @NotEmpty private final List<Item> items;
    @Positive private final BigDecimal total;
    // ctor + getters
}

@CommandHandler(CreateOrderCommand.class)
public class CreateOrderHandler implements ICommandHandler<CreateOrderCommand> {

    private final OrderPersistor orders;
    private final MediatorBus mediator;

    public CreateOrderHandler(OrderPersistor orders, MediatorBus mediator) {
        this.orders = orders;
        this.mediator = mediator;
    }

    @Override
    public void execute(CreateOrderCommand cmd) {
        var order = new Order(UUID.randomUUID().toString(), cmd.getCustomerId(),
                              cmd.getItems(), cmd.getTotal());
        orders.save(order);
        mediator.publish(new OrderPlacedEvent(order.getId(), cmd.getCustomerId(), cmd.getTotal()));
    }
}
```

`@CommandHandler` is meta-annotated with `@Component`, so the handler is a normal
Spring bean.

### Queries

A query is a request for data. One query type, one handler, returns `R`.

```java
public class GetOrderQuery implements IQuery<Order> {
    private final String orderId;
    public GetOrderQuery(String orderId) { this.orderId = orderId; }
    public String getOrderId() { return orderId; }
}

@QueryHandler(GetOrderQuery.class)
public class GetOrderHandler implements IQueryHandler<GetOrderQuery, Order> {
    private final OrderPersistor orders;
    public GetOrderHandler(OrderPersistor orders) { this.orders = orders; }

    @Override
    public Order execute(GetOrderQuery q) {
        return Optional.ofNullable(orders.findById(q.getOrderId()))
                .orElseThrow(() -> new OrderNotFoundException(q.getOrderId()));
    }
}
```

### Events

Events are facts. Many consumers per event are allowed.

```java
public class OrderPlacedEvent implements IEvent {
    private final String orderId;
    private final String customerId;
    private final BigDecimal total;
    // ctor + getters
}

@EventHandler(OrderPlacedEvent.class)
public class SendConfirmationEmailHandler implements IEventConsumer<OrderPlacedEvent> {
    @Override public void handle(OrderPlacedEvent event) { /* … */ }
}
```

By default consumers run **non-critically** (asynchronously, fire-and-forget).
Add `@NonCritical` for explicitness, or `@Critical(order = N)` to opt into the critical
phase.

### Critical vs. non-critical consumers and compensation

Within a single `publish(event)` call, the `EventBus` runs three phases:

1. **System consumers** (event-store persistence and anything else registered via
   `HandlerRegistry.registerSystemConsumer`) — sequential, before user code.
2. **Critical consumers** — sequential, sorted by `@Critical(order = ...)`. If consumer
   *N* throws, all previously successful critical consumers (`0..N-1`) are
   **compensated in reverse order**. The whole publish call then rethrows.
3. **Non-critical consumers** — submitted to `mediatorAsyncExecutor` and not awaited.

A critical consumer can implement `ICriticalEventConsumer<T>` and override either:

- `applyCompensatingEvent(event)` — return a new event to publish (preferred,
  because the compensation also gets written to the event store), or
- `compensate(event)` — perform direct cleanup.

```java
@EventHandler(OrderPlacedEvent.class)
@Critical(order = 1)
public class ReserveInventoryHandler implements ICriticalEventConsumer<OrderPlacedEvent> {
    @Override public void handle(OrderPlacedEvent event) { /* reserve stock */ }

    @Override
    public IEvent applyCompensatingEvent(OrderPlacedEvent event) {
        return new InventoryReleasedEvent(event.getOrderId());
    }
}
```

`mediatorBus.publish(event)` returns an `EventPublishResult` describing how many
consumers ran and how many compensations were triggered.

### Pipeline behaviors

A `IPipelineBehavior<TRequest, TResponse>` wraps command/query execution like
middleware. Lower priority runs **first / outermost**.

```java
@PipelineBehavior(priority = 50, scope = BehaviorScope.COMMAND)
public class AuditLoggingBehavior implements IPipelineBehavior<Object, Object> {
    @Override
    public Object handle(Object request, Supplier<Object> next) {
        log.info("[AUDIT] start {}", request.getClass().getSimpleName());
        try { return next.get(); }
        finally { log.info("[AUDIT] end"); }
    }
}
```

`scope` is `COMMAND`, `QUERY`, or `ALL`. Behaviors only fire on commands/queries,
not on events.

### Built-in behaviors

Each is opt-in via configuration and registered with a fixed priority:

| Behavior | Property | Priority | Notes |
| --- | --- | --- | --- |
| `ExceptionHandlingBehavior` | `mediator.behaviors.exception-handling-enabled` | −100 (outermost) | Logs and re-throws. |
| `LoggingBehavior` | `mediator.behaviors.logging-enabled` | 0 | Logs start/end + elapsed ms. |
| `PerformanceBehavior` | `mediator.behaviors.performance-tracking-enabled` (+ `performance-threshold-ms`, default 500) | 10 | Warns when slower than threshold. |
| `ValidationBehavior` | `mediator.behaviors.validation-enabled` | 100 | Uses `jakarta.validation.Validator`. Throws `ValidationException` (List of `ValidationError(property, message, code, value)`). |

### Skipping behaviors per request

```java
@SkipBehavior({ ValidationBehavior.class, LoggingBehavior.class })
public class InternalBootstrapCommand implements ICommand { /* … */ }
```

### Mediator context (correlation / causation)

Each top-level `send/query/publish` opens a new `MediatorContext` (random `correlationId`).
While dispatching events, the consuming phase sets `causationId` to the publishing event's
ID (`MediatorContextManager.runWithCausation`). Context propagates through child threads
because `MediatorContextManager` uses an `InheritableThreadLocal`. Inject
`MediatorContextManager` to read `getCorrelationId()` / `getCausationId()` from inside
behaviors or handlers.

---

## Event store

Set `mediator.event-store.enabled=true` and pick a `mode`:

- `mode: audit` — every published event is logged to the events table without
  optimistic-concurrency sequencing. Use this for an immutable audit trail next to a
  traditional state-of-the-world database.
- `mode: source` — used together with `AggregateRoot` / `AggregateRepository`. Each
  event for an aggregate gets a monotonically increasing `sequence_number`, and the
  current state is rebuilt by replaying events.

The schema is created automatically (`auto-create-schema: true`, default) by
`EventStoreSchemaManager`:

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
-- + indexes on (aggregate_type, aggregate_id, sequence_number),
--   event_type, correlation_id, occurred_at.
```

The repository is selected at startup:

- If JPA is on the classpath → `JpaEventStoreRepository` (default; works with whatever
  `EntityManager` the host app provides).
- If only `JdbcTemplate` is on the classpath, or `mediator.event-store.strategy=jdbc`,
  → `JdbcEventStoreRepository`.
- Set `use-existing-datasource: false` to provision a dedicated 3-connection
  `HikariDataSource` named `mediator-event-store` from `mediator.event-store.url`.

Both implementations expect Postgres-compatible `JSONB` syntax (`?::jsonb`).

### Audit mode

In audit mode, `EventStorePersistenceConsumer` writes the event but does not assign a
`sequence_number`. Every consumer downstream of the system phase still runs as
described above.

### Source mode (event sourcing)

Annotate the event with `@DomainEvent` so `EventStorePersistenceConsumer` can extract
the aggregate id:

```java
@DomainEvent(aggregateType = "BankAccount", aggregateIdField = "accountId")
public class AccountOpenedEvent implements IEvent { /* … */ }
```

In `source` mode the consumer also calls `repository.getNextSequence(...)` and assigns
a monotonically increasing `sequence_number` per `(aggregate_type, aggregate_id)`.

### Aggregate root and repository

```java
public class BankAccount extends AggregateRoot<String> {

    private String accountId;
    private BigDecimal balance = BigDecimal.ZERO;
    private boolean closed = false;

    public static BankAccount open(String id, String owner, BigDecimal initialDeposit) {
        BankAccount a = new BankAccount();
        a.apply(new AccountOpenedEvent(id, owner, initialDeposit));
        return a;
    }

    public void deposit(BigDecimal amount) {
        if (closed) throw new IllegalStateException();
        apply(new MoneyDepositedEvent(accountId, amount));
    }

    @Override public String getId() { return accountId; }
    @Override public String getAggregateType() { return "BankAccount"; }

    // Convention: applyXxxEvent(XxxEvent) — discovered via reflection.
    private void applyAccountOpenedEvent(AccountOpenedEvent e) {
        this.accountId = e.getAccountId();
        this.balance   = e.getInitialDeposit();
    }
    private void applyMoneyDepositedEvent(MoneyDepositedEvent e) {
        this.balance = balance.add(e.getAmount());
    }
}
```

```java
@Component
@ForAggregate(BankAccount.class)
public class BankAccountRepository extends AggregateRepository<BankAccount, String> {

    public BankAccountRepository(IEventStoreRepository es, MediatorBus bus) { super(es, bus); }

    @Override protected BankAccount createEmptyAggregate() { return new BankAccount(); }
    @Override protected String getAggregateType() { return "BankAccount"; }
    @Override
    protected IEvent deserializeEvent(String type, Map<String,Object> payload) {
        return switch (type) {
            case "AccountOpenedEvent"  -> new AccountOpenedEvent(/* … */);
            case "MoneyDepositedEvent" -> new MoneyDepositedEvent(/* … */);
            default -> null;
        };
    }
}
```

`AggregateRoot.apply(event)` records uncommitted events. `AggregateRepository.save(...)`
publishes each uncommitted event via the mediator (so the event store consumer
persists it and downstream consumers run), then clears the buffer.
`findById/getById` rebuild state from the stored event stream by calling the
matching `applyXxxEvent` method on the aggregate by reflection.

---

## Flow tracing

When `mediator.flow.enabled=true` the library posts:

- the service topology (registered commands / queries / events / behaviors) to
  `POST <collector-url>/collect/topology` once at startup, with retry/backoff
  (max 10 attempts, capped at 60s); and
- per-step events (`COMMAND_DISPATCHED`, `CRITICAL_CONSUMER_FAILED`,
  `COMPENSATION_STARTED`, …) batched to `POST <collector-url>/collect/steps`.

The collector is intentionally **not** part of this library — implement it in any
language you prefer (Node, Go, Python, Java, …). It just needs to accept those two
JSON payloads. Configuration:

```yaml
mediator:
  flow:
    enabled: true
    collector-url: http://localhost:4800
    service-name: my-service
    batch-size: 50
    flush-interval-ms: 2000
    include-payloads: false
    http-timeout-ms: 3000
```

Payload shapes:

```jsonc
// POST /collect/topology
{
  "serviceName": "my-service",
  "instanceId":  "uuid",
  "bootedAt":    "2026-05-05T12:34:56Z",
  "commands":    { "CreateOrderCommand": "CreateOrderHandler", ... },
  "queries":     { "GetOrderQuery": "GetOrderHandler", ... },
  "events":      ["OrderPlacedEvent", "InventoryReleasedEvent", ...],
  "behaviors":   [{ "name": "LoggingBehavior", "priority": 0, "scope": "ALL" }, ...]
}

// POST /collect/steps
{
  "instanceId":  "uuid",
  "serviceName": "my-service",
  "steps": [
    { "stepId": "uuid", "instanceId": "uuid",
      "type": "COMMAND_DISPATCHED", "name": "CreateOrderCommand",
      "timestamp": "2026-05-05T12:35:00.123Z" },
    ...
  ]
}
```

---

## Configuration reference

All keys live under `mediator.*`:

```yaml
mediator:
  behaviors:
    logging-enabled: false                # LoggingBehavior
    validation-enabled: false             # ValidationBehavior (needs jakarta-validation)
    exception-handling-enabled: false     # ExceptionHandlingBehavior
    performance-tracking-enabled: false   # PerformanceBehavior
    performance-threshold-ms: 500         # only logs when slower than this

  event-store:
    enabled: false                        # master switch
    mode: audit                           # audit | source
    strategy:                             # jdbc | jpa | (auto)
    table-name: domain_events
    use-existing-datasource: true         # false → dedicated HikariDataSource
    url:                                  # only used if use-existing-datasource=false
    auto-create-schema: true              # creates table+indexes on startup

  flow:
    enabled: false
    collector-url: http://localhost:4800
    service-name: unknown
    batch-size: 50
    flush-interval-ms: 2000
    include-payloads: false
    http-timeout-ms: 3000
```

You can also override the async executor for non-critical event consumers by
declaring your own `AsyncTaskExecutor` bean named `mediatorAsyncExecutor`.

---

## Examples

`example-audit/` and `example-source/` sit at the repo root but are **standalone
Gradle builds** — each has its own `settings.gradle.kts`, `build.gradle.kts`,
and Gradle wrapper. They consume the library through its real Maven coordinate
(`io.github.springmediator:spring-mediator-starter:1.0.0-SNAPSHOT`), exactly as
a downstream user would. They are *not* subprojects of the root build.

Workflow:

```bash
# 1) From repo root — publish the 3 library artifacts to your local Maven cache (~/.m2).
./gradlew clean publishToMavenLocal

# 2) Bring up Postgres for the example you want to run.
cd example-audit && docker compose up -d         # → audit example DB on :5435
# or
cd example-source && docker compose up -d        # → source example DB on :5436

# 3) Boot the example using its own wrapper. It pulls the library from mavenLocal.
cd example-audit  && ./gradlew bootRun           # listens on :8081
# or
cd example-source && ./gradlew bootRun           # listens on :8082
```

You can verify the example resolves the library as an external dependency:

```bash
cd example-audit && ./gradlew dependencies --configuration runtimeClasspath \
  | grep springmediator
```

— output shows `io.github.springmediator:spring-mediator-starter:1.0.0-SNAPSHOT`
pulling `core` + `autoconfigure` transitively.

What each example demonstrates:

- `example-audit` — `mode: audit`. `OrderController` shows `mediator.send` /
  `mediator.query`. Demonstrates a custom `AuditLoggingBehavior` and a critical
  consumer (`ReserveInventoryHandler`) with compensation
  (`HandleInventoryReleasedHandler`). `InternalsController` exposes registered
  handlers via `MediatorBus.getRegistered*()`.
- `example-source` — `mode: source`. `BankAccount` extends `AggregateRoot<String>`,
  `BankAccountRepository` extends `AggregateRepository<BankAccount, String>` and the
  command handlers persist by calling `repository.save(account)`.

---

## Consuming the library

### Gradle (Kotlin DSL)

```kotlin
repositories { mavenCentral() }

dependencies {
    implementation("io.github.springmediator:spring-mediator-starter:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories { mavenCentral() }

dependencies {
    implementation 'io.github.springmediator:spring-mediator-starter:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.springmediator</groupId>
    <artifactId>spring-mediator-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

If you don't want the auto-configuration (e.g. you're not on Spring Boot), depend
directly on `spring-mediator-core` and wire the buses yourself.
