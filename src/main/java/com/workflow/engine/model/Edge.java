package com.workflow.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents an edge (connection) between two nodes in a workflow.
 *
 * An edge connects a source node to a target node, defining either control flow
 * (execution ordering) or data flow (passing items between nodes). Edges support
 * port-based connections for nodes with multiple input/output ports.
 *
 * Fields:
 * - source: ID of the source (originating) node
 * - target: ID of the target (destination) node
 * - sourceHandle: Optional port identifier on the source node (for multi-port nodes)
 * - targetHandle: Optional port identifier on the target node (for multi-port nodes)
 * - isControl: True for control flow edges, false for data flow edges
 *
 * Control vs Data Flow:
 * - Control edges define execution ordering (when one node executes relative to another)
 * - Data edges define data passing (items flow from source to target nodes)
 *
 * Thread safety: Not thread-safe. Intended for use during workflow definition and planning.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see WorkflowDefinition
 * @see NodeDefinition
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Edge {
    /** ID of the source (originating) node */
    private String source;

    /** ID of the target (destination) node */
    private String target;

    /** Optional port/handle identifier on the source node for multi-port scenarios */
    private String sourceHandle;

    /** Optional port/handle identifier on the target node for multi-port scenarios */
    private String targetHandle;

    /** Edge type: "control" for control flow edges, "data" for data flow edges */
    private String type;

    /** True if this is a control flow edge, false if it's a data flow edge */
    @JsonProperty("isControl")
    private boolean control;

    public boolean isControl() {
        return control;
    }

    public void setIsControl(boolean control) {
        this.control = control;
    }
}
