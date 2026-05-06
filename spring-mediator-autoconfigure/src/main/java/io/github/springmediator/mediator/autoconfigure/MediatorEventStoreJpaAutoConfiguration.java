package io.github.springmediator.mediator.autoconfigure;

import io.github.springmediator.mediator.eventstore.IEventStoreRepository;
import io.github.springmediator.mediator.eventstore.JpaEventStoreRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = MediatorEventStoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "mediator.event-store", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@ConditionalOnMissingBean(IEventStoreRepository.class)
@EnableConfigurationProperties(MediatorProperties.class)
public class MediatorEventStoreJpaAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "mediator.event-store", name = "strategy",
            havingValue = "jpa", matchIfMissing = true)
    public IEventStoreRepository jpaEventStoreRepository(
            EntityManager entityManager, MediatorProperties properties) {
        return new JpaEventStoreRepository(entityManager,
                properties.getEventStore().getTableName(),
                properties.getEventStore().getMode());
    }
}
