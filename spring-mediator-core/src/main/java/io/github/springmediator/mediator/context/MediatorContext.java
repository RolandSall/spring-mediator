package io.github.springmediator.mediator.context;

import java.util.UUID;

public class MediatorContext {

    private final String correlationId;
    private String causationId;
    private String currentEventId;
    private String currentHandlerName;

    public MediatorContext() {
        this.correlationId = UUID.randomUUID().toString();
    }

    public MediatorContext(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getCurrentEventId() { return currentEventId; }
    public String getCurrentHandlerName() { return currentHandlerName; }

    public void setCausationId(String causationId) { this.causationId = causationId; }
    public void setCurrentEventId(String currentEventId) { this.currentEventId = currentEventId; }
    public void setCurrentHandlerName(String currentHandlerName) { this.currentHandlerName = currentHandlerName; }
}
