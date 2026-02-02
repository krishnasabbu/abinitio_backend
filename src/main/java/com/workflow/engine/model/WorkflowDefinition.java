package com.workflow.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * Represents a complete workflow definition.
 *
 * A workflow is a directed acyclic graph (DAG) of nodes connected by edges.
 * The workflow defines the execution sequence, data flow, and control logic
 * for processing.
 *
 * The definition consists of:
 * - Nodes: Individual execution units (steps) of the workflow
 * - Edges: Connections between nodes specifying control flow and data dependencies
 *
 * Fields:
 * - id: Unique identifier for this workflow
 * - name: Human-readable name for this workflow
 * - nodes: List of all nodes (steps) in this workflow
 * - edges: List of all edges (connections) between nodes
 *
 * Before execution, the workflow definition must be validated using GraphValidator
 * to ensure it forms a valid DAG without cycles and has all required executors.
 *
 * Thread safety: Not thread-safe. Intended for use during workflow definition and planning.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see NodeDefinition
 * @see Edge
 * @see GraphValidator
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowDefinition {
    /** Unique identifier for this workflow */
    @JsonIgnore
    private String id;

    private String workflowId;

    /** Human-readable name for this workflow */
    private String name;

    /** List of all nodes (execution units) in this workflow */
    private List<NodeDefinition> nodes;

    /** List of all edges (connections) between nodes */
    private List<Edge> edges;
}
