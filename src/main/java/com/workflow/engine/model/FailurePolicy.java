package com.workflow.engine.model;

import lombok.Data;

@Data
public class FailurePolicy {
    private FailureAction action = FailureAction.STOP;
    private Integer maxRetries = 3;
    private Long retryDelay = 1000L;
    private String routeToNode;
    private boolean skipOnError = false;
}
