package io.github.springmediator.mediator.exceptions;

public class ConcurrencyException extends RuntimeException {

    private final String aggregateType;
    private final String aggregateId;
    private final long expectedVersion;
    private final long actualVersion;

    public ConcurrencyException(String aggregateType, String aggregateId,
                                long expectedVersion, long actualVersion) {
        super("Concurrency conflict for " + aggregateType + "[" + aggregateId
                + "]: expected version " + expectedVersion + " but was " + actualVersion);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public long getExpectedVersion() { return expectedVersion; }
    public long getActualVersion() { return actualVersion; }
}
