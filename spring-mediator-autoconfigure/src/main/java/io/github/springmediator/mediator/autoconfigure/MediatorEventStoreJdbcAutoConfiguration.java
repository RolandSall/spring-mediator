package io.github.springmediator.mediator.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.github.springmediator.mediator.eventstore.IEventStoreRepository;
import io.github.springmediator.mediator.eventstore.JdbcEventStoreRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration(after = MediatorEventStoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "mediator.event-store", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnMissingBean(IEventStoreRepository.class)
@EnableConfigurationProperties(MediatorProperties.class)
public class MediatorEventStoreJdbcAutoConfiguration {

    @Bean("mediatorEventStoreDataSource")
    @ConditionalOnProperty(prefix = "mediator.event-store",
            name = "use-existing-datasource", havingValue = "false")
    @ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
    public DataSource mediatorEventStoreDataSource(MediatorProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(properties.getEventStore().getUrl());
        ds.setMaximumPoolSize(3);
        ds.setPoolName("mediator-event-store");
        return ds;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mediator.event-store", name = "strategy",
            havingValue = "jdbc")
    public IEventStoreRepository jdbcEventStoreRepository(
            DataSource dataSource, MediatorProperties properties) {
        return new JdbcEventStoreRepository(new JdbcTemplate(dataSource),
                properties.getEventStore().getTableName(),
                properties.getEventStore().getMode());
    }

    @Bean
    @ConditionalOnMissingClass("jakarta.persistence.EntityManager")
    @ConditionalOnMissingBean(IEventStoreRepository.class)
    public IEventStoreRepository jdbcEventStoreRepositoryFallback(
            DataSource dataSource, MediatorProperties properties) {
        return new JdbcEventStoreRepository(new JdbcTemplate(dataSource),
                properties.getEventStore().getTableName(),
                properties.getEventStore().getMode());
    }
}
