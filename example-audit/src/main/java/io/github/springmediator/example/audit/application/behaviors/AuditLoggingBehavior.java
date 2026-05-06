package io.github.springmediator.example.audit.application.behaviors;

import io.github.springmediator.mediator.annotations.BehaviorScope;
import io.github.springmediator.mediator.annotations.PipelineBehavior;
import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@PipelineBehavior(priority = 50, scope = BehaviorScope.COMMAND)
public class AuditLoggingBehavior implements IPipelineBehavior<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingBehavior.class);

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        String name = request.getClass().getSimpleName();
        log.info("[AUDIT] Executing command: {}", name);
        try {
            Object result = next.get();
            log.info("[AUDIT] Command {} completed successfully", name);
            return result;
        } catch (Exception e) {
            log.error("[AUDIT] Command {} failed: {}", name, e.getMessage());
            throw e;
        }
    }
}
