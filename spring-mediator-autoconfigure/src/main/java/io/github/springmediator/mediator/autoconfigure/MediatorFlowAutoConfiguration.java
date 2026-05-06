package io.github.springmediator.mediator.autoconfigure;

import io.github.springmediator.mediator.bus.MediatorBus;
import io.github.springmediator.mediator.flow.StepEmitter;
import io.github.springmediator.mediator.flow.TopologyCollector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MediatorAutoConfiguration.class)
@EnableConfigurationProperties(MediatorProperties.class)
@ConditionalOnProperty(prefix = "mediator.flow", name = "enabled", havingValue = "true")
public class MediatorFlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StepEmitter stepEmitter(MediatorProperties properties) {
        StepEmitter emitter = new StepEmitter();
        MediatorProperties.Flow flow = properties.getFlow();
        emitter.configure(
                flow.getCollectorUrl(),
                flow.getServiceName(),
                flow.getBatchSize(),
                flow.getFlushIntervalMs(),
                flow.isIncludePayloads(),
                flow.getHttpTimeoutMs());
        return emitter;
    }

    @Bean(initMethod = "start", destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public TopologyCollector topologyCollector(StepEmitter emitter, MediatorBus mediatorBus,
                                               MediatorProperties properties) {
        return new TopologyCollector(emitter, mediatorBus, properties.getFlow().getCollectorUrl());
    }
}
