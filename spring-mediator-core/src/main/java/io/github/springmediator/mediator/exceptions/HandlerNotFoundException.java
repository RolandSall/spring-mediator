package io.github.springmediator.mediator.exceptions;

public class HandlerNotFoundException extends RuntimeException {

    private final String requestName;
    private final String requestType;

    public HandlerNotFoundException(String requestName, String requestType) {
        super("No handler found for " + requestType + ": " + requestName);
        this.requestName = requestName;
        this.requestType = requestType;
    }

    public String getRequestName() {
        return requestName;
    }

    public String getRequestType() {
        return requestType;
    }
}
