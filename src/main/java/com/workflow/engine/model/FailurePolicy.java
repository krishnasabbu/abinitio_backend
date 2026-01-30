package com.workflow.engine.model;

import lombok.Data;

/**
 * Defines how a node should handle execution failures.
 *
 * Specifies the failure handling strategy for a node, including actions to take
 * when execution fails, retry logic, and error recovery routing.
 *
 * Fields:
 * - action: Primary action to take on failure (STOP, RETRY, ROUTE, etc.)
 * - maxRetries: Maximum number of retry attempts (default: 3)
 * - retryDelay: Delay in milliseconds between retry attempts (default: 1000)
 * - routeToNode: Node ID to route execution to on failure (for ROUTE action)
 * - skipOnError: If true, skip this node on error and continue; if false, stop execution
 *
 * Failure Actions:
 * - STOP: Stop execution and fail the entire workflow
 * - RETRY: Retry the node execution with backoff
 * - ROUTE: Route execution to a different node (error handler)
 * - SKIP: Skip this node and continue with downstream nodes
 *
 * Thread safety: Not thread-safe. Intended for use during workflow definition.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see FailureAction
 * @see NodeDefinition
 */
@Data
public class FailurePolicy {
    /** Primary action to take when this node fails (default: STOP) */
    private FailureAction action = FailureAction.STOP;

    /** Maximum number of retry attempts before giving up (default: 3) */
    private Integer maxRetries = 3;

    /** Delay in milliseconds between retry attempts (default: 1000ms = 1 second) */
    private Long retryDelay = 1000L;

    /** Target node ID for ROUTE action when failure occurs (error handler node) */
    private String routeToNode;

    /** If true, skip failed nodes and continue execution; if false, stop on error (default: false) */
    private boolean skipOnError = false;
}
