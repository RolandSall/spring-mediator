package io.github.springmediator.example.audit.presentation;

import io.github.springmediator.example.audit.application.behaviors.CachingBehavior;
import io.github.springmediator.mediator.bus.MediatorBus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internals")
public class InternalsController {

    private final MediatorBus mediatorBus;
    private final JdbcTemplate jdbcTemplate;

    public InternalsController(MediatorBus mediatorBus, JdbcTemplate jdbcTemplate) {
        this.mediatorBus = mediatorBus;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> getAuditEvents() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM audit_events ORDER BY stored_at DESC LIMIT 50");
        } catch (Exception e) {
            return List.of(Map.of("error", "Event store not available: " + e.getMessage()));
        }
    }

    @GetMapping("/events")
    public Map<String, Object> getRegisteredEvents() {
        return Map.of(
                "commands", mediatorBus.getRegisteredCommands(),
                "queries", mediatorBus.getRegisteredQueries(),
                "events", mediatorBus.getRegisteredEvents(),
                "behaviors", mediatorBus.getRegisteredBehaviors().stream()
                        .map(b -> Map.of(
                                "name", b.getName(),
                                "priority", b.getPriority(),
                                "scope", b.getScope().name()))
                        .toList()
        );
    }

    @PostMapping("/cache/clear")
    public Map<String, String> clearCache() {
        CachingBehavior.clearCache();
        return Map.of("status", "cache_cleared");
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "mode", "audit",
                "eventStoreEnabled", true,
                "registeredCommands", mediatorBus.getRegisteredCommands().size(),
                "registeredQueries", mediatorBus.getRegisteredQueries().size(),
                "registeredEvents", mediatorBus.getRegisteredEvents().size()
        );
    }
}
