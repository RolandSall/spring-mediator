package io.github.springmediator.mediator.bus;

import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.core.ICriticalEventConsumer;
import io.github.springmediator.mediator.core.IEvent;
import io.github.springmediator.mediator.core.IEventConsumer;
import io.github.springmediator.mediator.flow.StepEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.*;

/**
 * Dispatches events to consumers via HandlerRegistry.
 * Manages critical/non-critical execution order and compensation.
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final HandlerRegistry registry;
    private final StepEmitter stepEmitter;
    private final MediatorContextManager contextManager;
    private final AsyncTaskExecutor asyncExecutor;

    public EventBus(HandlerRegistry registry, StepEmitter stepEmitter,
                    MediatorContextManager contextManager,
                    AsyncTaskExecutor asyncExecutor) {
        this.registry = registry;
        this.stepEmitter = stepEmitter;
        this.contextManager = contextManager;
        this.asyncExecutor = asyncExecutor;
    }

    @SuppressWarnings("unchecked")
    public EventPublishResult publish(IEvent event) {
        String eventName = event.getClass().getSimpleName();
        String eventId = UUID.randomUUID().toString();

        if (stepEmitter != null) {
            stepEmitter.emit(StepEmitter.StepType.EVENT_PUBLISHED, eventName);
        }

        // Phase 1: System consumers (e.g., event store persistence)
        for (SystemConsumer sys : registry.getSystemConsumers()) {
            if (stepEmitter != null) {
                stepEmitter.emit(StepEmitter.StepType.SYSTEM_CONSUMER_STARTED, sys.getName());
            }
            try {
                contextManager.runWithCausation(eventId, () -> {
                    sys.getConsumer().handle(event);
                    return null;
                });
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.SYSTEM_CONSUMER_COMPLETED, sys.getName());
                }
            } catch (Exception e) {
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.SYSTEM_CONSUMER_FAILED, sys.getName(), e);
                }
                throw e;
            }
        }

        List<RegisteredEventConsumer> consumers = registry.getEventConsumers(eventName);
        if (consumers.isEmpty()) {
            return new EventPublishResult(0, 0, 0, 0);
        }

        // Separate critical and non-critical
        List<RegisteredEventConsumer> critical = consumers.stream()
                .filter(c -> c.getCriticality() == EventCriticality.CRITICAL)
                .sorted(Comparator.comparingInt(RegisteredEventConsumer::getOrder))
                .toList();

        List<RegisteredEventConsumer> nonCritical = consumers.stream()
                .filter(c -> c.getCriticality() == EventCriticality.NON_CRITICAL)
                .toList();

        int criticalSucceeded = 0;
        int compensationsRun = 0;
        List<RegisteredEventConsumer> succeeded = new ArrayList<>();

        // Phase 2: Critical consumers (sequential, ordered)
        for (RegisteredEventConsumer consumer : critical) {
            if (stepEmitter != null) {
                stepEmitter.emit(StepEmitter.StepType.CRITICAL_CONSUMER_STARTED, consumer.getName());
            }
            try {
                contextManager.runWithCausation(eventId, () -> {
                    ((IEventConsumer<IEvent>) consumer.getInstance()).handle(event);
                    return null;
                });
                criticalSucceeded++;
                succeeded.add(consumer);
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.CRITICAL_CONSUMER_COMPLETED, consumer.getName());
                }
            } catch (Exception e) {
                if (stepEmitter != null) {
                    stepEmitter.emit(StepEmitter.StepType.CRITICAL_CONSUMER_FAILED, consumer.getName(), e);
                }
                compensationsRun = runCompensations(succeeded, event);
                throw new RuntimeException(
                        "Critical consumer " + consumer.getName() + " failed. " +
                        compensationsRun + " compensation(s) executed.", e);
            }
        }

        // Phase 3: Non-critical consumers (async/fire-and-forget)
        int nonCriticalDispatched = nonCritical.size();
        for (RegisteredEventConsumer consumer : nonCritical) {
            if (stepEmitter != null) {
                stepEmitter.emit(StepEmitter.StepType.NONCRITICAL_CONSUMER_DISPATCHED, consumer.getName());
            }
            Runnable task = () -> {
                try {
                    ((IEventConsumer<IEvent>) consumer.getInstance()).handle(event);
                    if (stepEmitter != null) {
                        stepEmitter.emit(StepEmitter.StepType.NONCRITICAL_CONSUMER_COMPLETED, consumer.getName());
                    }
                } catch (Exception e) {
                    log.error("Non-critical consumer {} failed for event {}: {}",
                            consumer.getName(), eventName, e.getMessage(), e);
                    if (stepEmitter != null) {
                        stepEmitter.emit(StepEmitter.StepType.NONCRITICAL_CONSUMER_FAILED, consumer.getName(), e);
                    }
                }
            };

            if (asyncExecutor != null) {
                asyncExecutor.execute(task);
            } else {
                task.run();
            }
        }

        return new EventPublishResult(consumers.size(), criticalSucceeded,
                nonCriticalDispatched, compensationsRun);
    }

    @SuppressWarnings("unchecked")
    private int runCompensations(List<RegisteredEventConsumer> succeeded, IEvent event) {
        int compensationsRun = 0;
        for (int i = succeeded.size() - 1; i >= 0; i--) {
            RegisteredEventConsumer consumer = succeeded.get(i);
            if (consumer.getInstance() instanceof ICriticalEventConsumer<?>) {
                ICriticalEventConsumer<IEvent> critical =
                        (ICriticalEventConsumer<IEvent>) consumer.getInstance();
                try {
                    if (stepEmitter != null) {
                        stepEmitter.emit(StepEmitter.StepType.COMPENSATION_STARTED, consumer.getName());
                    }
                    IEvent compensatingEvent = critical.applyCompensatingEvent(event);
                    if (compensatingEvent != null) {
                        publish(compensatingEvent);
                        if (stepEmitter != null) {
                            stepEmitter.emit(StepEmitter.StepType.COMPENSATING_EVENT_PUBLISHED,
                                    compensatingEvent.getClass().getSimpleName());
                        }
                    } else {
                        critical.compensate(event);
                    }
                    compensationsRun++;
                    if (stepEmitter != null) {
                        stepEmitter.emit(StepEmitter.StepType.COMPENSATION_COMPLETED, consumer.getName());
                    }
                } catch (Exception compEx) {
                    log.error("Compensation failed for consumer {}: {}",
                            consumer.getName(), compEx.getMessage(), compEx);
                    if (stepEmitter != null) {
                        stepEmitter.emit(StepEmitter.StepType.COMPENSATION_FAILED, consumer.getName(), compEx);
                    }
                }
            }
        }
        return compensationsRun;
    }
}
