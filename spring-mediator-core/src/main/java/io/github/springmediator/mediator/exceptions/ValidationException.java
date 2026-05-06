package io.github.springmediator.mediator.exceptions;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public ValidationException(List<ValidationError> errors) {
        super("Validation failed: " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public List<ValidationError> getErrorsForProperty(String property) {
        return errors.stream()
                .filter(e -> property.equals(e.getProperty()))
                .toList();
    }

    public boolean hasErrorForProperty(String property) {
        return errors.stream().anyMatch(e -> property.equals(e.getProperty()));
    }

    public record ValidationError(
            String property,
            String message,
            String code,
            Object value
    ) {
        public String getProperty() { return property; }
        public String getMessage() { return message; }
    }
}
