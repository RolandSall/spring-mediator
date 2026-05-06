package io.github.springmediator.mediator.bus;

public class EventPublishResult {

    private final int totalHandlers;
    private final int criticalSucceeded;
    private final int nonCriticalDispatched;
    private final int compensationsRun;

    public EventPublishResult(int totalHandlers, int criticalSucceeded,
                              int nonCriticalDispatched, int compensationsRun) {
        this.totalHandlers = totalHandlers;
        this.criticalSucceeded = criticalSucceeded;
        this.nonCriticalDispatched = nonCriticalDispatched;
        this.compensationsRun = compensationsRun;
    }

    public int getTotalHandlers() { return totalHandlers; }
    public int getCriticalSucceeded() { return criticalSucceeded; }
    public int getNonCriticalDispatched() { return nonCriticalDispatched; }
    public int getCompensationsRun() { return compensationsRun; }
}
