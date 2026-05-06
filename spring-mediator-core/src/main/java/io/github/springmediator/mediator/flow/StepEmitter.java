package io.github.springmediator.mediator.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StepEmitter {

    private static final Logger log = LoggerFactory.getLogger(StepEmitter.class);
    private static final int MAX_BUFFER_SIZE = 1000;

    private final String instanceId = UUID.randomUUID().toString();
    private final List<Map<String, Object>> buffer = new CopyOnWriteArrayList<>();
    private final HttpClient httpClient;

    private boolean enabled = false;
    private String collectorUrl;
    private String serviceName = "unknown";
    private int batchSize = 50;
    private boolean includePayloads = false;
    private int httpTimeoutMs = 3000;

    private ScheduledExecutorService scheduler;

    public StepEmitter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void configure(String collectorUrl, String serviceName,
                          int batchSize, int flushIntervalMs,
                          boolean includePayloads, int httpTimeoutMs) {
        this.collectorUrl = collectorUrl;
        this.serviceName = serviceName != null ? serviceName : "unknown";
        this.batchSize = batchSize;
        this.includePayloads = includePayloads;
        this.httpTimeoutMs = httpTimeoutMs;
        this.enabled = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mediator-flow-emitter");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void emit(StepType type, String name) {
        emit(type, name, null);
    }

    public void emit(StepType type, String name, Exception error) {
        if (!enabled) return;

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepId", UUID.randomUUID().toString());
        step.put("instanceId", instanceId);
        step.put("type", type.name());
        step.put("timestamp", Instant.now().toString());
        step.put("name", name);
        if (error != null) {
            step.put("error", error.getMessage());
        }

        if (buffer.size() < MAX_BUFFER_SIZE) {
            buffer.add(step);
        }

        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    public void flush() {
        if (buffer.isEmpty()) return;

        List<Map<String, Object>> batch = new ArrayList<>(buffer);
        buffer.clear();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instanceId", instanceId);
        payload.put("serviceName", serviceName);
        payload.put("steps", batch);

        try {
            String json = toJson(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectorUrl + "/collect/steps"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(httpTimeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("Failed to flush steps to collector: {}", e.getMessage());
        }
    }

    public void destroy() {
        flush();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public String getInstanceId() { return instanceId; }
    public String getServiceName() { return serviceName; }

    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, map);
        return sb.toString();
    }

    private void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(escapeJson(entry.getKey().toString())).append("\":");
                appendJson(sb, entry.getValue());
                first = false;
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                appendJson(sb, list.get(i));
            }
            sb.append(']');
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escapeJson(value.toString())).append('"');
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public enum StepType {
        COMMAND_DISPATCHED,
        COMMAND_HANDLER_STARTED,
        COMMAND_HANDLER_COMPLETED,
        COMMAND_HANDLER_FAILED,
        QUERY_DISPATCHED,
        QUERY_HANDLER_STARTED,
        QUERY_HANDLER_COMPLETED,
        QUERY_HANDLER_FAILED,
        EVENT_PUBLISHED,
        COMPENSATING_EVENT_PUBLISHED,
        SYSTEM_CONSUMER_STARTED,
        SYSTEM_CONSUMER_COMPLETED,
        SYSTEM_CONSUMER_FAILED,
        CRITICAL_CONSUMER_STARTED,
        CRITICAL_CONSUMER_COMPLETED,
        CRITICAL_CONSUMER_FAILED,
        NONCRITICAL_CONSUMER_DISPATCHED,
        NONCRITICAL_CONSUMER_COMPLETED,
        NONCRITICAL_CONSUMER_FAILED,
        BEHAVIOR_ENTERED,
        BEHAVIOR_COMPLETED,
        BEHAVIOR_FAILED,
        RETRY_ATTEMPTED,
        COMPENSATION_STARTED,
        COMPENSATION_COMPLETED,
        COMPENSATION_FAILED
    }
}
