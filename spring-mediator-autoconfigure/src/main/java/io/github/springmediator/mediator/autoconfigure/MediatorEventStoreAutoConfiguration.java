package io.github.springmediator.mediator.autoconfigure;

import io.github.springmediator.mediator.bus.HandlerRegistry;
import io.github.springmediator.mediator.context.MediatorContextManager;
import io.github.springmediator.mediator.eventstore.EventStorePersistenceConsumer;
import io.github.springmediator.mediator.eventstore.EventStoreSchemaManager;
import io.github.springmediator.mediator.eventstore.IEventStoreRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(after = MediatorAutoConfiguration.class)
@EnableConfigurationProperties(MediatorProperties.class)
@ConditionalOnProperty(prefix = "mediator.event-store", name = "enabled", havingValue = "true")
public class MediatorEventStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(IEventStoreRepository.class)
    @ConditionalOnMissingBean(EventStorePersistenceConsumer.class)
    public EventStorePersistenceConsumer eventStorePersistenceConsumer(
            IEventStoreRepository repository,
            MediatorContextManager contextManager,
            MediatorProperties properties,
            HandlerRegistry registry) {
        EventStorePersistenceConsumer consumer = new EventStorePersistenceConsumer(
                repository, contextManager, properties.getEventStore().getMode());
        registry.registerSystemConsumer(consumer, "EventStorePersistenceConsumer");
        return consumer;
    }

    @Bean(initMethod = "initialize")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "mediator.event-store", name = "auto-create-schema",
            havingValue = "true", matchIfMissing = true)
    public EventStoreSchemaManager eventStoreSchemaManager(
            DataSource dataSource, MediatorProperties properties) {
        return new EventStoreSchemaManager(dataSource, properties.getEventStore().getTableName());
    }
}
