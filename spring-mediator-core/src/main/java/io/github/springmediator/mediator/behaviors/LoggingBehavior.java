package io.github.springmediator.mediator.behaviors;

import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class LoggingBehavior implements IPipelineBehavior<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger("MediatorBus");

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        String name = request.getClass().getSimpleName();
        log.info("Handling {}...", name);
        long start = System.currentTimeMillis();
        try {
            Object result = next.get();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Handled {} successfully in {}ms", name, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Failed handling {} after {}ms: {}", name, elapsed, e.getMessage());
            throw e;
        }
    }
}
