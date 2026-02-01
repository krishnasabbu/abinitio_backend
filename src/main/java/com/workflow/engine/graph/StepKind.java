package com.workflow.engine.graph;

/**
 * Defines the control-flow semantics of a step node in the execution graph.
 *
 * StepKind determines how DynamicJobBuilder wires the step into the Spring Batch
 * flow graph. This is distinct from StepClassification which describes the
 * data-processing nature of the node.
 *
 * Fork/Join Semantics:
 * - FORK: Spawns multiple parallel branches. All downstream nextSteps execute concurrently.
 * - JOIN: Synchronization barrier. Waits for ALL upstream branches to complete before proceeding.
 * - NORMAL: Standard sequential step with single-path execution.
 * - BARRIER: Lightweight synchronization point (like JOIN but may allow partial completions).
 *
 * Usage in DynamicJobBuilder:
 * - FORK nodes trigger split() with TaskExecutor for parallel execution
 * - JOIN nodes are wired as the convergence point after split branches complete
 * - NORMAL nodes chain sequentially via on("*").to(nextStep)
 *
 * @see DynamicJobBuilder
 * @see StepNode
 */
public enum StepKind {
    /**
     * Normal sequential step. Executes and transitions to next step(s) based on
     * execution hints (SERIAL = chain, PARALLEL = split).
     */
    NORMAL,

    /**
     * Fork point that spawns parallel branches. All nextSteps execute concurrently
     * in a split() flow. Typically paired with a downstream JOIN.
     */
    FORK,

    /**
     * Join barrier that synchronizes parallel branches. Waits for ALL upstream
     * branches feeding into this node to complete before executing.
     * The join step itself executes only once after all branches complete.
     */
    JOIN,

    /**
     * Lightweight barrier for partial synchronization. Similar to JOIN but may
     * be configured to proceed after N-of-M branches complete.
     * Reserved for future use.
     */
    BARRIER,

    /**
     * Conditional routing step. Routes to different downstream paths based on
     * a JobExecutionDecider evaluation.
     */
    DECISION,

    /**
     * Subgraph invocation point. Represents an embedded workflow that executes
     * as a unit. Reserved for subgraph expansion.
     */
    SUBGRAPH
}
