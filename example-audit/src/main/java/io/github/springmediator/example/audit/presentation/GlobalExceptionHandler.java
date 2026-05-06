package io.github.springmediator.example.audit.presentation;

import io.github.springmediator.example.audit.domain.exceptions.OrderNotFoundException;
import io.github.springmediator.mediator.exceptions.HandlerNotFoundException;
import io.github.springmediator.mediator.exceptions.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(HandlerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleHandlerNotFound(HandlerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Validation failed",
                        "details", ex.getErrors().stream()
                                .map(e -> Map.of("property", e.property(), "message", e.message()))
                                .toList()
                ));
    }
}
