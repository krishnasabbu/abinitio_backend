package com.workflow.engine.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * - data: Optional nested data object (supports frontend UI workflows with data.config nesting)
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

    /** Optional nested data object (for workflows with data.config structure) */
    private JsonNode data;

    /** Optional execution hints for planning and optimization */
    private ExecutionHints executionHints;

    /** Optional metrics collection configuration */
    private MetricsConfig metrics;

    /** Failure handling policy for this node */
    private FailurePolicy onFailure;

    @JsonAnySetter
    private void handleUnknownProperty(String key, JsonNode value) {
        if ("data".equals(key) && value != null && value.has("config")) {
            this.data = value;
            if (this.config == null) {
                this.config = value.get("config");
            }
        }
    }

    public JsonNode getResolvedConfig() {
        if (this.config != null) {
            return this.config;
        }
        if (this.data != null && this.data.has("config")) {
            return this.data.get("config");
        }
        return null;
    }

    public void setConfig(JsonNode config) {
        this.config = config;
    }
}
