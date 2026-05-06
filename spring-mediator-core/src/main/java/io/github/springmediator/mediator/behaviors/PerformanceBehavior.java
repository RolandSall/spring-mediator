package io.github.springmediator.mediator.behaviors;

import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class PerformanceBehavior implements IPipelineBehavior<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger("MediatorBus");

    private final int thresholdMs;

    public PerformanceBehavior(int thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        String name = request.getClass().getSimpleName();
        long start = System.currentTimeMillis();
        Object result = next.get();
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > thresholdMs) {
            log.warn("Slow request detected: {} took {}ms (threshold: {}ms)",
                    name, elapsed, thresholdMs);
        }
        return result;
    }
}
