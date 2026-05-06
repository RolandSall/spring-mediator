# Functional documentation — using Spring Mediator

This document is a task-oriented guide. It tells you **how to do the things** Spring
Mediator was built to do, with copy-pasteable code. For the why and the
implementation details, see [TECHNICAL.md](./TECHNICAL.md).

> Spring Mediator is a Spring Boot 3 application messaging fabric. It gives you
> commands, queries, and events going through a single mediator with optional
> middleware (validation, logging, performance), event-store persistence (audit or
> event-sourcing), critical/non-critical event consumers with **automatic
> compensation**, and correlation/causation tracing across the whole flow.

---

## Table of contents

1. [Add it to your project](#1-add-it-to-your-project)
2. [Configure what you want](#2-configure-what-you-want)
3. [Recipe — write a command](#3-recipe--write-a-command)
4. [Recipe — write a query](#4-recipe--write-a-query)
5. [Recipe — publish a domain event](#5-recipe--publish-a-domain-event)
6. [Recipe — make a consumer transactional with compensation](#6-recipe--make-a-consumer-transactional-with-compensation)
7. [Recipe — add a custom pipeline behavior](#7-recipe--add-a-custom-pipeline-behavior)
8. [Recipe — skip a behavior on one specific request](#8-recipe--skip-a-behavior-on-one-specific-request)
9. [Recipe — turn on the audit-mode event store](#9-recipe--turn-on-the-audit-mode-event-store)
10. [Recipe — write an event-sourced aggregate](#10-recipe--write-an-event-sourced-aggregate)
11. [Recipe — read correlation / causation IDs in your handlers](#11-recipe--read-correlation--causation-ids-in-your-handlers)
12. [Recipe — introspect what is registered at runtime](#12-recipe--introspect-what-is-registered-at-runtime)
13. [Recipe — emit flow tracing to a custom collector](#13-recipe--emit-flow-tracing-to-a-custom-collector)
14. [Common errors and what they mean](#14-common-errors-and-what-they-mean)

---

## 1. Add it to your project

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.springmediator:spring-mediator-starter:1.0.0")

    // Only if you turn validation-enabled on:
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Only if you turn the event store on with JDBC:
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // (Optional) The examples use Lombok for terseness:
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
```

Maven coordinates: `io.github.springmediator:spring-mediator-starter:1.0.0`.

The starter pulls `spring-mediator-core` + `spring-mediator-autoconfigure`. There is
**no `@EnableMediator` annotation** to add — auto-configuration registers everything
on Spring Boot startup.

---

## 2. Configure what you want

`application.yml`:

```yaml
mediator:
  behaviors:                          # all default to false; pick what you need
    logging-enabled: true
    validation-enabled: true
    exception-handling-enabled: true
    performance-tracking-enabled: true
    performance-threshold-ms: 100

  event-store:
    enabled: false                    # turn on to persist every published event
    mode: audit                       # audit | source
    table-name: domain_events
    use-existing-datasource: true     # false → dedicated mediator-event-store HikariDataSource
    auto-create-schema: true

  flow:
    enabled: false                    # turn on to emit topology + steps to a remote collector
    collector-url: http://localhost:4800
    service-name: my-service
    batch-size: 50
    flush-interval-ms: 2000
    include-payloads: false
    http-timeout-ms: 3000
```

Everything is `@ConditionalOnMissingBean` — you can override any single piece with
your own bean.

---

## 3. Recipe — write a command

A command is **intent to change state**. One command class, one handler, no return.

```java
// 1) define the command (Lombok @Getter + @AllArgsConstructor for terseness)
@Getter
@AllArgsConstructor
public class CreateOrderCommand implements ICommand {
    @NotBlank private final String customerId;
    @NotEmpty private final List<Item> items;
    @Positive private final BigDecimal total;

    public record Item(String productId, String name, int quantity, BigDecimal price) {}
}

// 2) write its handler — annotated, no manual registration needed
@CommandHandler(CreateOrderCommand.class)
public class CreateOrderHandler implements ICommandHandler<CreateOrderCommand> {

    private final OrderRepository orders;
    private final MediatorBus mediator;

    public CreateOrderHandler(OrderRepository orders, MediatorBus mediator) {
        this.orders   = orders;
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

// 3) dispatch from anywhere (controller, scheduled job, another handler, …)
mediator.send(new CreateOrderCommand(customerId, items, total));
```

`@CommandHandler` is meta-annotated with `@Component`, so the handler is a normal
Spring bean — constructor injection, `@Transactional`, `@Async`, profile beans, etc.
all work as usual.

---

## 4. Recipe — write a query

A query is a **request for data**. One query class, one handler, returns `R`.

```java
@Getter
@AllArgsConstructor
public class GetOrderQuery implements IQuery<Order> {
    private final String orderId;
}

@QueryHandler(GetOrderQuery.class)
public class GetOrderHandler implements IQueryHandler<GetOrderQuery, Order> {
    private final OrderRepository orders;

    public GetOrderHandler(OrderRepository orders) { this.orders = orders; }

    @Override
    public Order execute(GetOrderQuery q) {
        return Optional.ofNullable(orders.findById(q.getOrderId()))
                       .orElseThrow(() -> new OrderNotFoundException(q.getOrderId()));
    }
}

Order order = mediator.query(new GetOrderQuery(id));
```

---

## 5. Recipe — publish a domain event

Events are **facts** — many consumers per event are allowed.

```java
@Getter
@AllArgsConstructor
public class OrderPlacedEvent implements IEvent {
    private final String orderId;
    private final String customerId;
    private final BigDecimal total;
}

@EventHandler(OrderPlacedEvent.class)            // default = non-critical
public class SendConfirmationEmailHandler implements IEventConsumer<OrderPlacedEvent> {
    @Override public void handle(OrderPlacedEvent e) { /* … */ }
}

mediator.publish(new OrderPlacedEvent(orderId, customerId, total));
```

By default consumers run **non-critically** — submitted to a virtual-thread executor,
fire-and-forget, failures only logged.

---

## 6. Recipe — make a consumer transactional with compensation

When an event triggers a chain of side effects that **must succeed together**,
mark each consumer with `@Critical(order = N)`. The bus runs them sequentially in
order. If consumer #N throws, **all previously-succeeded critical consumers
(0..N-1) are compensated in reverse order** before the publish call rethrows.

```java
@EventHandler(OrderPlacedEvent.class)
@Critical(order = 1)
public class ReserveInventoryHandler implements ICriticalEventConsumer<OrderPlacedEvent> {

    @Override
    public void handle(OrderPlacedEvent e) {
        inventory.reserve(e.getOrderId(), e.getItems());
    }

    @Override                                       // preferred: emit a compensating event
    public IEvent applyCompensatingEvent(OrderPlacedEvent e) {
        return new InventoryReleasedEvent(e.getOrderId());
    }
}

@EventHandler(OrderPlacedEvent.class)
@Critical(order = 2)
public class ChargePaymentHandler implements ICriticalEventConsumer<OrderPlacedEvent> {

    @Override
    public void handle(OrderPlacedEvent e) {
        payments.charge(e.getOrderId(), e.getTotal());     // <-- if this throws…
    }

    @Override
    public IEvent applyCompensatingEvent(OrderPlacedEvent e) {
        return new PaymentRefundedEvent(e.getOrderId(), e.getTotal());
    }
}
```

If `ChargePaymentHandler` fails, the bus publishes `InventoryReleasedEvent`
automatically (running its own consumers for free) and rethrows.

`mediator.publish(...)` returns an `EventPublishResult` describing how many
consumers ran and how many compensations triggered.

> Compensating events are persisted to the event store like any other event — your
> audit trail captures both the failure and the rollback.

---

## 7. Recipe — add a custom pipeline behavior

Behaviors are middleware around commands and queries. Lower priority runs first
(outermost wrapper).

```java
@PipelineBehavior(priority = 50, scope = BehaviorScope.COMMAND)
public class AuditLoggingBehavior implements IPipelineBehavior<Object, Object> {

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        var name = request.getClass().getSimpleName();
        log.info("[AUDIT] start {}", name);
        try { return next.get(); }
        finally { log.info("[AUDIT] end   {}", name); }
    }
}
```

`scope = COMMAND | QUERY | ALL`. Behaviors don't run on events.

Built-ins ship at fixed priorities — see [TECHNICAL.md → Pipeline](./TECHNICAL.md#5-pipeline-orchestrator).

---

## 8. Recipe — skip a behavior on one specific request

Useful when a request shouldn't go through validation, or when an internal
bootstrap command needs to bypass logging.

```java
@SkipBehavior({ ValidationBehavior.class, LoggingBehavior.class })
public class ReplayCommand implements ICommand { /* … */ }
```

---

## 9. Recipe — turn on the audit-mode event store

```yaml
mediator:
  event-store:
    enabled: true
    mode: audit
    table-name: domain_events
    auto-create-schema: true
```

That's it — every `mediator.publish(event)` now writes the event to a JSONB-typed
Postgres table **before** any user consumer runs, with `correlation_id`,
`causation_id`, `occurred_at`, `stored_at`, `event_type`, full payload, and
optional `metadata`.

If JPA is on the classpath, `JpaEventStoreRepository` is auto-selected. If only
JDBC is, `JdbcEventStoreRepository` is used. If both — pick with
`mediator.event-store.strategy=jdbc|jpa`.

To use a separate dedicated database for the event store, set
`use-existing-datasource: false` and `url: jdbc:postgresql://…`.

---

## 10. Recipe — write an event-sourced aggregate

In **`mode: source`**, the aggregate's state is rebuilt by replaying its event stream.

```java
@DomainEvent(aggregateType = "BankAccount", aggregateIdField = "accountId")
@Getter
@AllArgsConstructor
public class AccountOpenedEvent implements IEvent {
    private final String accountId;
    private final String ownerName;
    private final BigDecimal initialDeposit;
}
```

```java
@Getter
public class BankAccount extends AggregateRoot<String> {

    private String accountId;
    private String ownerName;
    private BigDecimal balance = BigDecimal.ZERO;
    private boolean closed;

    public static BankAccount open(String id, String owner, BigDecimal initialDeposit) {
        BankAccount a = new BankAccount();
        a.apply(new AccountOpenedEvent(id, owner, initialDeposit));   // records uncommitted event
        return a;
    }

    public void deposit(BigDecimal amount) {
        if (closed) throw new IllegalStateException();
        apply(new MoneyDepositedEvent(accountId, amount));
    }

    @Override public String getId() { return accountId; }
    @Override public String getAggregateType() { return "BankAccount"; }

    // Convention: applyXxxEvent(XxxEvent) — discovered by reflection.
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
    @Override protected String getAggregateType()          { return "BankAccount"; }

    @Override
    protected IEvent deserializeEvent(String type, Map<String, Object> payload) {
        return switch (type) {
            case "AccountOpenedEvent" -> new AccountOpenedEvent(
                    (String) payload.get("accountId"),
                    (String) payload.get("ownerName"),
                    new BigDecimal(payload.get("initialDeposit").toString()));
            case "MoneyDepositedEvent" -> new MoneyDepositedEvent(
                    (String) payload.get("accountId"),
                    new BigDecimal(payload.get("amount").toString()));
            default -> null;
        };
    }
}
```

Usage in the command handler:

```java
@CommandHandler(OpenAccountCommand.class)
public class OpenAccountHandler implements ICommandHandler<OpenAccountCommand> {

    private final BankAccountRepository repo;
    public OpenAccountHandler(BankAccountRepository repo) { this.repo = repo; }

    @Override
    public void execute(OpenAccountCommand cmd) {
        var id = UUID.randomUUID().toString();
        var account = BankAccount.open(id, cmd.getOwnerName(), cmd.getInitialDeposit());
        repo.save(account);                  // publishes each uncommitted event via the mediator
    }
}
```

`save(...)` publishes each uncommitted event via the bus. The event-store consumer
writes them with monotonic `sequence_number` per `(aggregate_type, aggregate_id)`
in `source` mode. `findById/getById` rebuild state from the persisted stream.

---

## 11. Recipe — read correlation / causation IDs in your handlers

```java
@Component
public class TraceLoggingBehavior implements IPipelineBehavior<Object, Object> {

    private final MediatorContextManager ctx;
    public TraceLoggingBehavior(MediatorContextManager ctx) { this.ctx = ctx; }

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        MDC.put("correlationId", ctx.getCorrelationId());
        if (ctx.getCausationId() != null) MDC.put("causationId", ctx.getCausationId());
        try { return next.get(); }
        finally { MDC.clear(); }
    }
}
```

The same correlation ID is reused across all events published from inside one
top-level command/query. The causation ID becomes the publishing event's ID, so a
chain `Cmd → EvtA → consumer publishes EvtB → consumer publishes EvtC` ends up with
`correlationId = T0, causationId = id(EvtB)` on the third event.

---

## 12. Recipe — introspect what is registered at runtime

```java
@RestController
@RequestMapping("/internals")
class InternalsController {
    private final MediatorBus mediator;
    public InternalsController(MediatorBus m) { this.mediator = m; }

    @GetMapping("/handlers")
    Map<String, Object> handlers() {
        return Map.of(
            "commands",  mediator.getRegisteredCommandsDetailed(),
            "queries",   mediator.getRegisteredQueriesDetailed(),
            "events",    mediator.getRegisteredEvents(),
            "behaviors", mediator.getRegisteredBehaviors().stream()
                .map(b -> Map.of("name", b.getName(),
                                 "priority", b.getPriority(),
                                 "scope", b.getScope().name()))
                .toList()
        );
    }
}
```

Useful for exposing a `/admin/topology` endpoint, generating diagrams, asserting in
integration tests that no handler is missing, etc.

---

## 13. Recipe — emit flow tracing to a custom collector

```yaml
mediator:
  flow:
    enabled: true
    collector-url: http://your-collector:4800
    service-name: orders-service
    include-payloads: false
```

The library will POST:

- once at startup, the topology (commands/queries/events/behaviors) →
  `<collector-url>/collect/topology`,
- continuously, batches of step events (`COMMAND_DISPATCHED`,
  `CRITICAL_CONSUMER_FAILED`, `COMPENSATION_STARTED`, …) →
  `<collector-url>/collect/steps`.

The collector can be implemented in **any language**. Payload shapes are documented
in the root [README → Flow tracing](../README.md#flow-tracing).

---

## 14. Common errors and what they mean

| Error | Likely cause | Fix |
| --- | --- | --- |
| `HandlerNotFoundException: No handler found for command: FooCommand` | The `@CommandHandler` (or `@QueryHandler`/`@EventHandler`) annotation is missing, or the bean is in a package not scanned by your `@SpringBootApplication`. | Add the annotation. Make sure your application class's package is an ancestor of the handler class's package. |
| `ValidationException: Validation failed: N error(s)` | Request failed jakarta validation. | Inspect `e.getErrors()`; each `ValidationError` has `property`, `message`, `code`, `value`. |
| `ConcurrencyException: Concurrency conflict for X[id]: expected version A but was B` | Two writers tried to append events for the same aggregate id at the same version (event-sourcing optimistic concurrency check). | Reload the aggregate, retry the command. |
| `Critical consumer X failed. N compensation(s) executed.` | A `@Critical` consumer threw; preceding critical consumers were compensated and the publish call is rethrowing. | Inspect the cause; check the compensation chain in your event store. |
| `Cannot resolve symbol 'ConditionalOnMissingBean'` (IDE only) | Stale Gradle import in the IDE. The build is fine. | IntelliJ → Gradle tool window → reload, or *File → Invalidate Caches & Restart*. |
