package io.github.springmediator.mediator.autoconfigure;

import io.github.springmediator.mediator.annotations.*;
import io.github.springmediator.mediator.bus.EventCriticality;
import io.github.springmediator.mediator.bus.HandlerRegistry;
import io.github.springmediator.mediator.core.*;
import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import io.github.springmediator.mediator.pipeline.RegisteredBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers handler beans via BeanPostProcessor and registers them with HandlerRegistry.
 * The handler instances are Spring-managed beans — the registry is just a lookup index.
 */
public class MediatorHandlerRegistrar implements BeanPostProcessor, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MediatorHandlerRegistrar.class);

    private final HandlerRegistry registry;
    private final List<PendingRegistration> pending = new ArrayList<>();

    public MediatorHandlerRegistrar(HandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();

        CommandHandler cmdAnn = AnnotationUtils.findAnnotation(beanClass, CommandHandler.class);
        if (cmdAnn != null && bean instanceof ICommandHandler<?>) {
            pending.add(new PendingRegistration(RegistrationType.COMMAND, bean, cmdAnn.value(), null));
        }

        QueryHandler queryAnn = AnnotationUtils.findAnnotation(beanClass, QueryHandler.class);
        if (queryAnn != null && bean instanceof IQueryHandler<?, ?>) {
            pending.add(new PendingRegistration(RegistrationType.QUERY, bean, queryAnn.value(), null));
        }

        EventHandler eventAnn = AnnotationUtils.findAnnotation(beanClass, EventHandler.class);
        if (eventAnn != null && bean instanceof IEventConsumer<?>) {
            Critical critical = AnnotationUtils.findAnnotation(beanClass, Critical.class);
            pending.add(new PendingRegistration(RegistrationType.EVENT, bean, eventAnn.value(), critical));
        }

        PipelineBehavior behaviorAnn = AnnotationUtils.findAnnotation(beanClass, PipelineBehavior.class);
        if (behaviorAnn != null && bean instanceof IPipelineBehavior<?, ?>) {
            pending.add(new PendingRegistration(RegistrationType.BEHAVIOR, bean, null, behaviorAnn));
        }

        return bean;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterSingletonsInstantiated() {
        for (PendingRegistration reg : pending) {
            switch (reg.type) {
                case COMMAND -> {
                    registry.registerCommandHandler(
                            (Class<? extends ICommand>) reg.targetClass,
                            (ICommandHandler<?>) reg.bean);
                    log.info("Registered command handler: {} -> {}",
                            reg.targetClass.getSimpleName(), reg.bean.getClass().getSimpleName());
                }
                case QUERY -> {
                    registry.registerQueryHandler(
                            (Class<? extends IQuery<?>>) reg.targetClass,
                            (IQueryHandler<?, ?>) reg.bean);
                    log.info("Registered query handler: {} -> {}",
                            reg.targetClass.getSimpleName(), reg.bean.getClass().getSimpleName());
                }
                case EVENT -> {
                    Critical critical = (Critical) reg.extra;
                    EventCriticality criticality = critical != null
                            ? EventCriticality.CRITICAL : EventCriticality.NON_CRITICAL;
                    int order = critical != null ? critical.order() : 0;
                    registry.registerEventConsumer(
                            (Class<? extends IEvent>) reg.targetClass,
                            (IEventConsumer<?>) reg.bean, criticality, order);
                    log.info("Registered event consumer: {} -> {} ({})",
                            reg.targetClass.getSimpleName(), reg.bean.getClass().getSimpleName(), criticality);
                }
                case BEHAVIOR -> {
                    PipelineBehavior ann = (PipelineBehavior) reg.extra;
                    RegisteredBehavior behavior = new RegisteredBehavior(
                            (IPipelineBehavior<?, ?>) reg.bean,
                            ann.priority(), ann.scope(), null);
                    registry.registerPipelineBehavior(behavior);
                    log.info("Registered pipeline behavior: {} (priority={}, scope={})",
                            reg.bean.getClass().getSimpleName(), ann.priority(), ann.scope());
                }
            }
        }
        pending.clear();

        log.info("Mediator initialized: {} commands, {} queries, {} events, {} behaviors",
                registry.getRegisteredCommands().size(),
                registry.getRegisteredQueries().size(),
                registry.getRegisteredEvents().size(),
                registry.getRegisteredBehaviors().size());
    }

    private enum RegistrationType { COMMAND, QUERY, EVENT, BEHAVIOR }

    private record PendingRegistration(
            RegistrationType type,
            Object bean,
            Class<?> targetClass,
            Object extra
    ) {}
}
