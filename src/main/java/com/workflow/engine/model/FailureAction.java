package com.workflow.engine.model;

/**
 * Enumeration of failure handling actions for workflow nodes.
 *
 * Defines the available strategies for handling execution failures at the node level.
 * Each action determines how the workflow responds when a node execution fails.
 *
 * Values:
 * - STOP: Stop workflow execution immediately, fail the entire workflow
 * - SKIP: Skip this node and continue execution with downstream nodes
 * - RETRY: Retry the node execution (respects maxRetries and retryDelay from FailurePolicy)
 * - ROUTE: Route execution to an error handler node (specified by routeToNode)
 *
 * @author Workflow Engine
 * @version 1.0
 * @see FailurePolicy
 */
public enum FailureAction {
    /** Stop workflow execution immediately on failure */
    STOP,

    /** Skip the failed node and continue with downstream execution */
    SKIP,

    /** Retry the node execution with configured backoff and max attempts */
    RETRY,

    /** Route execution to an error handler node (alternative path) */
    ROUTE
}
