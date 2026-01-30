package com.workflow.engine.graph;

public class GraphValidationException extends RuntimeException {

    public GraphValidationException(String message) {
        super(message);
    }

    public GraphValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
