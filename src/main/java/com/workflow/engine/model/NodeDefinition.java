package com.workflow.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class NodeDefinition {
    private String id;
    private String type;
    private JsonNode config;
    private ExecutionHints executionHints;
    private MetricsConfig metrics;
    private FailurePolicy onFailure;
}
