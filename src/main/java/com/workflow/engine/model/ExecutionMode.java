package com.workflow.engine.model;

/**
 * Enumeration of execution modes for workflow nodes.
 *
 * Specifies how items are processed through a node - whether serially,
 * in parallel, or distributed across partitions.
 *
 * Values:
 * - SERIAL: Process items one at a time sequentially
 * - PARALLEL: Process items concurrently in multiple threads
 * - PARTITIONED: Distribute items across logical partitions for parallel processing
 *
 * @author Workflow Engine
 * @version 1.0
 * @see ExecutionHints
 */
public enum ExecutionMode {
    /** Process items sequentially, one at a time (default) */
    SERIAL,

    /** Process items concurrently in multiple threads for throughput */
    PARALLEL,

    /** Distribute items across partitions for distributed processing */
    PARTITIONED
}
