package io.github.springmediator.mediator.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "mediator")
@Validated
public class MediatorProperties {

    private Behaviors behaviors = new Behaviors();
    private EventStore eventStore = new EventStore();
    private Flow flow = new Flow();

    public Behaviors getBehaviors() { return behaviors; }
    public void setBehaviors(Behaviors behaviors) { this.behaviors = behaviors; }
    public EventStore getEventStore() { return eventStore; }
    public void setEventStore(EventStore eventStore) { this.eventStore = eventStore; }
    public Flow getFlow() { return flow; }
    public void setFlow(Flow flow) { this.flow = flow; }

    public static class Behaviors {
        private boolean loggingEnabled = false;
        private boolean validationEnabled = false;
        private boolean exceptionHandlingEnabled = false;
        private boolean performanceTrackingEnabled = false;
        private int performanceThresholdMs = 500;

        public boolean isLoggingEnabled() { return loggingEnabled; }
        public void setLoggingEnabled(boolean loggingEnabled) { this.loggingEnabled = loggingEnabled; }
        public boolean isValidationEnabled() { return validationEnabled; }
        public void setValidationEnabled(boolean validationEnabled) { this.validationEnabled = validationEnabled; }
        public boolean isExceptionHandlingEnabled() { return exceptionHandlingEnabled; }
        public void setExceptionHandlingEnabled(boolean exceptionHandlingEnabled) { this.exceptionHandlingEnabled = exceptionHandlingEnabled; }
        public boolean isPerformanceTrackingEnabled() { return performanceTrackingEnabled; }
        public void setPerformanceTrackingEnabled(boolean performanceTrackingEnabled) { this.performanceTrackingEnabled = performanceTrackingEnabled; }
        public int getPerformanceThresholdMs() { return performanceThresholdMs; }
        public void setPerformanceThresholdMs(int performanceThresholdMs) { this.performanceThresholdMs = performanceThresholdMs; }
    }

    public static class EventStore {
        private boolean enabled = false;
        private String mode = "audit";
        private String strategy;
        private String url;
        private String tableName = "domain_events";
        private boolean useExistingDatasource = true;
        private boolean autoCreateSchema = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public boolean isUseExistingDatasource() { return useExistingDatasource; }
        public void setUseExistingDatasource(boolean useExistingDatasource) { this.useExistingDatasource = useExistingDatasource; }
        public boolean isAutoCreateSchema() { return autoCreateSchema; }
        public void setAutoCreateSchema(boolean autoCreateSchema) { this.autoCreateSchema = autoCreateSchema; }
    }

    public static class Flow {
        private boolean enabled = false;
        private String collectorUrl = "http://localhost:4800";
        private String serviceName = "unknown";
        private int batchSize = 50;
        private int flushIntervalMs = 2000;
        private boolean includePayloads = false;
        private int httpTimeoutMs = 3000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCollectorUrl() { return collectorUrl; }
        public void setCollectorUrl(String collectorUrl) { this.collectorUrl = collectorUrl; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(int flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
        public boolean isIncludePayloads() { return includePayloads; }
        public void setIncludePayloads(boolean includePayloads) { this.includePayloads = includePayloads; }
        public int getHttpTimeoutMs() { return httpTimeoutMs; }
        public void setHttpTimeoutMs(int httpTimeoutMs) { this.httpTimeoutMs = httpTimeoutMs; }
    }
}
