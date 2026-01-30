package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;

/**
 * Core interface for all node executors in the workflow engine.
 *
 * Executors implement the logic for executing specific node types. Each executor
 * provides Spring Batch ItemReader, ItemProcessor, and ItemWriter implementations
 * for reading input, processing items, and writing output.
 *
 * The executor is responsible for:
 * - Creating a reader to fetch input items
 * - Creating a processor to transform/process items
 * - Creating a writer to persist/emit output items
 * - Validating node configuration before execution
 * - Declaring support for metrics and failure handling
 *
 * Generic parameters:
 * - I: Input item type
 * - O: Output item type (may be same as I for pass-through nodes)
 *
 * Thread safety: Implementations must be thread-safe as they may be invoked
 * concurrently for different node executions.
 *
 * @param <I> Input item type
 * @param <O> Output item type
 * @author Workflow Engine
 * @version 1.0
 */
public interface NodeExecutor<I, O> {

    /**
     * Creates an ItemReader for reading input items.
     *
     * The reader is responsible for fetching items from the node's input source
     * (file, database, API, etc.). Called once per node execution.
     *
     * @param context the node execution context containing configuration
     * @return an ItemReader implementation for this node
     * @throws IllegalArgumentException if configuration is invalid
     */
    ItemReader<I> createReader(NodeExecutionContext context);

    /**
     * Creates an ItemProcessor for transforming/processing items.
     *
     * The processor is called for each item read by the reader. It can transform,
     * enrich, filter, or aggregate items. For pass-through nodes, may return
     * an identity processor.
     *
     * @param context the node execution context containing configuration
     * @return an ItemProcessor implementation for this node
     */
    ItemProcessor<I, O> createProcessor(NodeExecutionContext context);

    /**
     * Creates an ItemWriter for writing/emitting output items.
     *
     * The writer is called to persist, send, or emit processed items to the
     * node's output destination (file, database, API, next node, etc.).
     *
     * @param context the node execution context containing configuration
     * @return an ItemWriter implementation for this node
     */
    ItemWriter<O> createWriter(NodeExecutionContext context);

    /**
     * Validates the node configuration before execution.
     *
     * Should check that all required configuration properties are present and
     * valid, and throw IllegalArgumentException if validation fails.
     *
     * @param context the node execution context containing configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    void validate(NodeExecutionContext context);

    /**
     * Declares whether this executor supports metrics collection.
     *
     * @return true if the executor can provide execution metrics, false otherwise
     */
    boolean supportsMetrics();

    /**
     * Declares whether this executor supports failure handling policies.
     *
     * @return true if the executor can handle failures and retries, false otherwise
     */
    boolean supportsFailureHandling();

    /**
     * Returns the node type identifier handled by this executor.
     *
     * This identifier is used to match the executor to nodes with the same type
     * in workflow definitions (e.g., "RestAPISource", "DBExecute", "Map").
     *
     * @return the node type string
     */
    String getNodeType();
}
