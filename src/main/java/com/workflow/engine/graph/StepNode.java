package com.workflow.engine.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.model.ExecutionHints;
import com.workflow.engine.model.FailurePolicy;
import com.workflow.engine.model.MetricsConfig;

import java.util.List;

public record StepNode(
    String nodeId,
    String nodeType,
    JsonNode config,
    List<String> nextSteps,
    List<String> errorSteps,
    MetricsConfig metrics,
    FailurePolicy exceptionHandling,
    ExecutionHints executionHints,
    StepClassification classification
) {}
