package io.github.springmediator.example.audit.application.behaviors;

import io.github.springmediator.mediator.annotations.BehaviorScope;
import io.github.springmediator.mediator.annotations.PipelineBehavior;
import io.github.springmediator.mediator.pipeline.IPipelineBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@PipelineBehavior(priority = 5, scope = BehaviorScope.QUERY)
public class CachingBehavior implements IPipelineBehavior<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(CachingBehavior.class);
    private static final long TTL_SECONDS = 30;

    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        String key = request.getClass().getSimpleName() + ":" + request.hashCode();

        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            log.debug("[CACHE] Hit for {}", key);
            return entry.value();
        }

        Object result = next.get();
        cache.put(key, new CacheEntry(result, Instant.now().plusSeconds(TTL_SECONDS)));
        log.debug("[CACHE] Miss for {} — cached", key);
        return result;
    }

    public static void clearCache() {
        cache.clear();
    }

    private record CacheEntry(Object value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
