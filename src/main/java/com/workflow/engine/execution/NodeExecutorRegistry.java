package com.workflow.engine.execution;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all node executor implementations in the workflow engine.
 *
 * Maintains a mapping of node type identifiers to their corresponding executor implementations.
 * Provides lookup and availability checking for executors at runtime. This registry is populated
 * by Spring's component scanning, which automatically discovers and registers all bean instances
 * that implement the NodeExecutor interface.
 *
 * Thread safety: Thread-safe for read operations after initialization. Registration should occur
 * during application startup before concurrent access.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class NodeExecutorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutorRegistry.class);

    private final Map<String, NodeExecutor<?, ?>> executors = new HashMap<>();

    /**
     * Registers a node executor in the registry.
     *
     * Maps the executor's node type identifier to the executor instance. If an executor
     * with the same node type already exists, it will be replaced.
     *
     * @param executor the executor to register
     */
    public void register(NodeExecutor<?, ?> executor) {
        String nodeType = executor.getNodeType();
        executors.put(nodeType, executor);
        logger.debug("Registered executor for node type: {}", nodeType);
    }

    /**
     * Retrieves an executor by its node type identifier.
     *
     * @param nodeType the node type identifier to lookup
     * @return the executor instance for the given node type
     * @throws IllegalArgumentException if no executor is registered for the node type
     */
    public NodeExecutor<?, ?> getExecutor(String nodeType) {
        logger.debug("Looking up executor for node type: {}", nodeType);
        NodeExecutor<?, ?> executor = executors.get(nodeType);
        if (executor == null) {
            logger.error("No executor registered for node type: {}", nodeType);
            throw new IllegalArgumentException("No executor registered for node type: " + nodeType);
        }
        return executor;
    }

    /**
     * Checks if an executor is registered for the given node type.
     *
     * @param nodeType the node type identifier to check
     * @return true if an executor is registered for this node type, false otherwise
     */
    public boolean hasExecutor(String nodeType) {
        boolean exists = executors.containsKey(nodeType);
        if (!exists) {
            logger.debug("No executor found for node type: {}", nodeType);
        }
        return exists;
    }

    /**
     * Unregisters (removes) an executor from the registry.
     *
     * @param nodeType the node type identifier whose executor should be removed
     */
    public void unregister(String nodeType) {
        if (executors.remove(nodeType) != null) {
            logger.debug("Unregistered executor for node type: {}", nodeType);
        } else {
            logger.warn("Attempted to unregister non-existent executor for node type: {}", nodeType);
        }
    }
}
