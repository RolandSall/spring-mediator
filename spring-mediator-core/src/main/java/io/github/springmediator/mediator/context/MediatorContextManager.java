package io.github.springmediator.mediator.context;

import java.util.function.Supplier;

/**
 * Manages mediator context using InheritableThreadLocal, equivalent to
 * AsyncLocalStorage in NestJS. Context propagates to child threads.
 */
public class MediatorContextManager {

    private final InheritableThreadLocal<MediatorContext> storage = new InheritableThreadLocal<>();

    /**
     * Run a callback within a new context (new correlationId).
     */
    public <T> T runWithNewContext(Supplier<T> callback) {
        MediatorContext previous = storage.get();
        try {
            storage.set(new MediatorContext());
            return callback.get();
        } finally {
            if (previous != null) {
                storage.set(previous);
            } else {
                storage.remove();
            }
        }
    }

    /**
     * Run a callback within a new context (new correlationId), void variant.
     */
    public void runWithNewContext(Runnable callback) {
        runWithNewContext(() -> {
            callback.run();
            return null;
        });
    }

    /**
     * Run a callback preserving the current correlationId but setting a new causationId.
     */
    public <T> T runWithCausation(String eventId, Supplier<T> callback) {
        MediatorContext ctx = storage.get();
        if (ctx == null) {
            ctx = new MediatorContext();
            storage.set(ctx);
        }
        String previousCausation = ctx.getCausationId();
        String previousEventId = ctx.getCurrentEventId();
        try {
            ctx.setCausationId(eventId);
            ctx.setCurrentEventId(eventId);
            return callback.get();
        } finally {
            ctx.setCausationId(previousCausation);
            ctx.setCurrentEventId(previousEventId);
        }
    }

    public MediatorContext getContext() {
        MediatorContext ctx = storage.get();
        if (ctx == null) {
            ctx = new MediatorContext();
            storage.set(ctx);
        }
        return ctx;
    }

    public boolean hasContext() {
        return storage.get() != null;
    }

    public String getCorrelationId() {
        return getContext().getCorrelationId();
    }

    public String getCausationId() {
        MediatorContext ctx = storage.get();
        return ctx != null ? ctx.getCausationId() : null;
    }
}
