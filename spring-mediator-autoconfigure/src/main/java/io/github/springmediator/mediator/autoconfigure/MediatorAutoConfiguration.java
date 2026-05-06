package io.github.springmediator.mediator.autoconfigure;

import io.github.springmediator.mediator.bus.*;
import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.flow.StepEmitter;
import io.github.springmediator.mediator.pipeline.PipelineOrchestrator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.Collections;

@AutoConfiguration
@EnableConfigurationProperties(MediatorProperties.class)
public class MediatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MediatorContextManager mediatorContextManager() {
        return new MediatorContextManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public PipelineOrchestrator pipelineOrchestrator() {
        return new PipelineOrchestrator(Collections.emptyList());
    }

    @Bean
    @ConditionalOnMissingBean
    public HandlerRegistry handlerRegistry(PipelineOrchestrator pipelineOrchestrator) {
        return new HandlerRegistry(pipelineOrchestrator);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandBus commandBus(HandlerRegistry registry,
                                 PipelineOrchestrator orchestrator,
                                 ObjectProvider<StepEmitter> stepEmitter,
                                 MediatorContextManager contextManager) {
        return new CommandBus(registry, orchestrator, stepEmitter.getIfAvailable(), contextManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryBus queryBus(HandlerRegistry registry,
                             PipelineOrchestrator orchestrator,
                             ObjectProvider<StepEmitter> stepEmitter,
                             MediatorContextManager contextManager) {
        return new QueryBus(registry, orchestrator, stepEmitter.getIfAvailable(), contextManager);
    }

    @Bean("mediatorAsyncExecutor")
    @ConditionalOnMissingBean(name = "mediatorAsyncExecutor")
    public AsyncTaskExecutor mediatorAsyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("mediator-async-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBus eventBus(HandlerRegistry registry,
                             ObjectProvider<StepEmitter> stepEmitter,
                             MediatorContextManager contextManager,
                             @Qualifier("mediatorAsyncExecutor") AsyncTaskExecutor mediatorAsyncExecutor) {
        return new EventBus(registry, stepEmitter.getIfAvailable(), contextManager, mediatorAsyncExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public MediatorBus mediatorBus(CommandBus commandBus, QueryBus queryBus,
                                   EventBus eventBus, MediatorContextManager contextManager,
                                   HandlerRegistry registry) {
        return new MediatorBus(commandBus, queryBus, eventBus, contextManager, registry);
    }

    @Bean
    public static MediatorHandlerRegistrar mediatorHandlerRegistrar(HandlerRegistry registry) {
        return new MediatorHandlerRegistrar(registry);
    }
}
