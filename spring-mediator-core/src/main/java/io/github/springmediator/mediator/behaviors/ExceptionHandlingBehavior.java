package io.github.springmediator.mediator.behaviors;

import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class ExceptionHandlingBehavior implements IPipelineBehavior<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger("MediatorBus");

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        try {
            return next.get();
        } catch (Exception e) {
            String requestName = request.getClass().getSimpleName();
            log.error("Exception in handler for {}: {}", requestName, e.getMessage(), e);
            throw e;
        }
    }
}
