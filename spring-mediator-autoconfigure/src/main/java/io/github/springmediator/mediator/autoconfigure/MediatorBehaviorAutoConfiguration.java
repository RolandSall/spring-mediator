package io.github.springmediator.mediator.autoconfigure;

import io.github.springmediator.mediator.annotations.BehaviorScope;
import io.github.springmediator.mediator.behaviors.ExceptionHandlingBehavior;
import io.github.springmediator.mediator.behaviors.LoggingBehavior;
import io.github.springmediator.mediator.behaviors.PerformanceBehavior;
import io.github.springmediator.mediator.behaviors.ValidationBehavior;
import io.github.springmediator.mediator.bus.HandlerRegistry;
import io.github.springmediator.mediator.pipeline.RegisteredBehavior;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import jakarta.validation.Validator;

@AutoConfiguration(after = MediatorAutoConfiguration.class)
@EnableConfigurationProperties(MediatorProperties.class)
public class MediatorBehaviorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "mediator.behaviors", name = "logging-enabled", havingValue = "true")
    @ConditionalOnMissingBean(LoggingBehavior.class)
    public LoggingBehavior loggingBehavior(HandlerRegistry registry) {
        LoggingBehavior behavior = new LoggingBehavior();
        registry.registerPipelineBehavior(
                new RegisteredBehavior(behavior, 0, BehaviorScope.ALL, null));
        return behavior;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mediator.behaviors", name = "validation-enabled", havingValue = "true")
    @ConditionalOnMissingBean(ValidationBehavior.class)
    @ConditionalOnClass(name = "jakarta.validation.Validator")
    public ValidationBehavior validationBehavior(Validator validator, HandlerRegistry registry) {
        ValidationBehavior behavior = new ValidationBehavior(validator);
        registry.registerPipelineBehavior(
                new RegisteredBehavior(behavior, 100, BehaviorScope.ALL, null));
        return behavior;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mediator.behaviors", name = "exception-handling-enabled", havingValue = "true")
    @ConditionalOnMissingBean(ExceptionHandlingBehavior.class)
    public ExceptionHandlingBehavior exceptionHandlingBehavior(HandlerRegistry registry) {
        ExceptionHandlingBehavior behavior = new ExceptionHandlingBehavior();
        registry.registerPipelineBehavior(
                new RegisteredBehavior(behavior, -100, BehaviorScope.ALL, null));
        return behavior;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mediator.behaviors", name = "performance-tracking-enabled", havingValue = "true")
    @ConditionalOnMissingBean(PerformanceBehavior.class)
    public PerformanceBehavior performanceBehavior(MediatorProperties properties, HandlerRegistry registry) {
        PerformanceBehavior behavior = new PerformanceBehavior(
                properties.getBehaviors().getPerformanceThresholdMs());
        registry.registerPipelineBehavior(
                new RegisteredBehavior(behavior, 10, BehaviorScope.ALL, null));
        return behavior;
    }
}
