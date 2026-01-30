package com.workflow.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Represents a single node (step) in a workflow definition.
 *
 * Each node is a discrete unit of execution within a workflow. Nodes are connected
 * by edges to form the workflow DAG. The node type determines which executor handles
 * the node's execution.
 *
 * Fields:
 * - id: Unique identifier for this node within the workflow
 * - type: Node type determining which executor implementation handles this node
 * - config: JSON configuration specific to this node type (executor-dependent)
 * - executionHints: Optional hints for execution planning (parallelism, distribution, etc.)
 * - metrics: Optional metrics collection configuration for this node
 * - onFailure: Failure handling policy (stop, skip, retry, etc.)
 *
 * Thread safety: Not thread-safe. Intended for use during workflow definition and planning phases.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Data
public class NodeDefinition {
    /** Unique identifier for this node */
    private String id;

    /** Node type determining executor implementation (e.g., "RestAPISource", "DBExecute", "Map") */
    private String type;

    /** Executor-specific configuration as JSON */
    private JsonNode config;

    /** Optional execution hints for planning and optimization */
    private ExecutionHints executionHints;

    /** Optional metrics collection configuration */
    private MetricsConfig metrics;

    /** Failure handling policy for this node */
    private FailurePolicy onFailure;
}
