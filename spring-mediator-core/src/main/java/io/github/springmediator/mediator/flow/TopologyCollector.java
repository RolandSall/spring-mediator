package io.github.springmediator.mediator.flow;

import io.github.springmediator.mediator.bus.MediatorBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends topology information (registered handlers, behaviors, etc.)
 * to the MediatorFlow collector server at startup with retry logic.
 */
public class TopologyCollector {

    private static final Logger log = LoggerFactory.getLogger(TopologyCollector.class);

    private final StepEmitter stepEmitter;
    private final MediatorBus mediatorBus;
    private final String collectorUrl;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private int retryCount = 0;
    private static final int MAX_RETRIES = 10;
    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long MAX_DELAY_SECONDS = 60;

    public TopologyCollector(StepEmitter stepEmitter, MediatorBus mediatorBus, String collectorUrl) {
        this.stepEmitter = stepEmitter;
        this.mediatorBus = mediatorBus;
        this.collectorUrl = collectorUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mediator-topology-collector");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.schedule(this::sendTopology, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void sendTopology() {
        try {
            Map<String, Object> topology = buildTopology();
            String json = toJson(topology);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(collectorUrl + "/collect/topology"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Topology sent to MediatorFlow collector at {}", collectorUrl);
            } else {
                retry("HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            retry(e.getMessage());
        }
    }

    private void retry(String reason) {
        retryCount++;
        if (retryCount > MAX_RETRIES) {
            log.warn("Failed to send topology after {} retries. Giving up.", MAX_RETRIES);
            return;
        }
        long delay = Math.min(INITIAL_DELAY_SECONDS * (1L << retryCount), MAX_DELAY_SECONDS);
        log.debug("Topology send failed ({}). Retrying in {}s (attempt {}/{})",
                reason, delay, retryCount, MAX_RETRIES);
        scheduler.schedule(this::sendTopology, delay, TimeUnit.SECONDS);
    }

    private Map<String, Object> buildTopology() {
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("serviceName", stepEmitter.getServiceName());
        topology.put("instanceId", stepEmitter.getInstanceId());
        topology.put("bootedAt", Instant.now().toString());
        topology.put("commands", mediatorBus.getRegisteredCommandsDetailed());
        topology.put("queries", mediatorBus.getRegisteredQueriesDetailed());
        topology.put("events", mediatorBus.getRegisteredEvents());
        topology.put("behaviors", mediatorBus.getRegisteredBehaviors().stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", b.getName());
                    m.put("priority", b.getPriority());
                    m.put("scope", b.getScope().name());
                    return m;
                }).toList());
        return topology;
    }

    public void destroy() {
        scheduler.shutdown();
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, map);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(entry.getKey().toString().replace("\"", "\\\"")).append("\":");
                appendJson(sb, entry.getValue());
                first = false;
            }
            sb.append('}');
        } else if (value instanceof java.util.List<?> list) {
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
            sb.append('"').append(value.toString().replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n")).append('"');
        }
    }
}
