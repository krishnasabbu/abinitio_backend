package com.workflow.engine.model;

import lombok.Data;

/**
 * Provides execution optimization hints for workflow nodes.
 *
 * Hints guide the execution engine's planning and optimization decisions,
 * such as parallelization, batching, and resource allocation. These are
 * suggestions rather than requirements.
 *
 * Fields:
 * - mode: Execution mode (SERIAL, PARALLEL, BATCH, etc.)
 * - chunkSize: Suggested batch/chunk size for processing items
 * - partitionCount: Suggested number of partitions for parallel processing
 * - maxRetries: Maximum retry attempts for this node (overrides FailurePolicy if set)
 * - timeout: Execution timeout in milliseconds
 *
 * Thread safety: Not thread-safe. Intended for use during workflow definition and planning.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see ExecutionMode
 * @see NodeDefinition
 */
@Data
public class ExecutionHints {
    /** Execution mode determining parallelization strategy (default: SERIAL) */
    private ExecutionMode mode = ExecutionMode.SERIAL;

    /** Suggested chunk/batch size for processing items (null means auto-determine) */
    private Integer chunkSize;

    /** Suggested number of partitions for parallel execution (null means auto-determine) */
    private Integer partitionCount;

    /** Maximum retry attempts for this node (overrides FailurePolicy if set) */
    private Integer maxRetries;

    /** Execution timeout in milliseconds (null means unlimited) */
    private Long timeout;

    /**
     * For FORK nodes: the explicit JOIN node ID where all branches must converge.
     * Required when mode=PARALLEL and node has multiple nextSteps.
     * The join node must have kind=JOIN.
     */
    private String joinNodeId;
}
