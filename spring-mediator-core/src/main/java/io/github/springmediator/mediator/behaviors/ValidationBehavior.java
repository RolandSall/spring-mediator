package io.github.springmediator.mediator.behaviors;

import io.github.springmediator.mediator.exceptions.ValidationException;
import io.github.springmediator.mediator.exceptions.ValidationException.ValidationError;
import io.github.springmediator.mediator.pipeline.IPipelineBehavior;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ValidationBehavior implements IPipelineBehavior<Object, Object> {

    private final Validator validator;

    public ValidationBehavior(Validator validator) {
        this.validator = validator;
    }

    @Override
    public Object handle(Object request, Supplier<Object> next) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<ValidationError> errors = violations.stream()
                    .map(v -> new ValidationError(
                            v.getPropertyPath().toString(),
                            v.getMessage(),
                            v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                            v.getInvalidValue()))
                    .toList();
            throw new ValidationException(errors);
        }
        return next.get();
    }
}
