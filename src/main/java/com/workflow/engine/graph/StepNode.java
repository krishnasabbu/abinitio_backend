package com.workflow.engine.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.OutputPort;
import com.workflow.engine.model.ExecutionHints;
import com.workflow.engine.model.FailurePolicy;
import com.workflow.engine.model.MetricsConfig;

import java.util.List;

/**
 * Immutable representation of a step in the execution graph.
 *
 * StepNode captures both data-processing configuration (nodeType, config) and
 * control-flow semantics (kind, nextSteps, errorSteps). The DynamicJobBuilder
 * uses kind() to determine fork/join wiring.
 *
 * Key fields for control-flow:
 * - kind: NORMAL, FORK, JOIN, DECISION - determines flow wiring strategy
 * - nextSteps: downstream steps on successful completion
 * - errorSteps: downstream steps on FAILED status
 * - upstreamSteps: for JOIN nodes, the branches that must complete before this executes
 *
 * @param nodeId Unique identifier for this step (used as Spring Batch step name)
 * @param nodeType Executor type identifier (e.g., "FileSource", "Join", "Filter")
 * @param config Node-specific configuration as JSON
 * @param nextSteps IDs of downstream steps on success
 * @param errorSteps IDs of downstream steps on failure
 * @param metrics Metrics collection configuration
 * @param exceptionHandling Retry and skip policies
 * @param executionHints Parallelization and chunk size hints
 * @param classification Data-processing classification (SOURCE, TRANSFORM, etc.)
 * @param outputPorts Multi-output port definitions for routing
 * @param kind Control-flow kind (NORMAL, FORK, JOIN, etc.)
 * @param upstreamSteps For JOIN nodes, list of upstream step IDs that feed into this join
 */
public record StepNode(
    String nodeId,
    String nodeType,
    JsonNode config,
    List<String> nextSteps,
    List<String> errorSteps,
    MetricsConfig metrics,
    FailurePolicy exceptionHandling,
    ExecutionHints executionHints,
    StepClassification classification,
    List<OutputPort> outputPorts,
    StepKind kind,
    List<String> upstreamSteps
) {
    public StepNode {
        if (kind == null) {
            kind = StepKind.NORMAL;
        }
    }

    public StepNode(
        String nodeId,
        String nodeType,
        JsonNode config,
        List<String> nextSteps,
        List<String> errorSteps,
        MetricsConfig metrics,
        FailurePolicy exceptionHandling,
        ExecutionHints executionHints,
        StepClassification classification,
        List<OutputPort> outputPorts
    ) {
        this(nodeId, nodeType, config, nextSteps, errorSteps, metrics,
             exceptionHandling, executionHints, classification, outputPorts,
             StepKind.NORMAL, null);
    }

    public boolean isFork() {
        return kind == StepKind.FORK ||
               (nextSteps != null && nextSteps.size() > 1 && isParallelMode());
    }

    public boolean isJoin() {
        return kind == StepKind.JOIN ||
               classification == StepClassification.JOIN;
    }

    public boolean isDecision() {
        return kind == StepKind.DECISION;
    }

    public boolean isSubgraph() {
        return kind == StepKind.SUBGRAPH;
    }

    private boolean isParallelMode() {
        return executionHints != null &&
               executionHints.getMode() == com.workflow.engine.model.ExecutionMode.PARALLEL;
    }

    public boolean hasErrorHandling() {
        return errorSteps != null && !errorSteps.isEmpty();
    }

    public boolean isTerminal() {
        return (nextSteps == null || nextSteps.isEmpty()) &&
               (errorSteps == null || errorSteps.isEmpty());
    }
}
