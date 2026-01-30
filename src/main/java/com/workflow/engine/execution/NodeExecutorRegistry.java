package com.workflow.engine.execution;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central registry for all node executor implementations in the workflow engine.
 *
 * Maintains a mapping of node type identifiers to their corresponding executor implementations.
 * Automatically discovers and registers all Spring beans implementing NodeExecutor at startup.
 * Provides lookup and availability checking for executors at runtime.
 *
 * Auto-registration (constructor injection):
 * - Spring injects all NodeExecutor beans
 * - Each executor's nodeType is extracted via getNodeType()
 * - Duplicate nodeTypes cause startup failure with detailed diagnostics
 * - Summary logged at startup showing all registered executor types
 *
 * Thread safety: Thread-safe for read operations after initialization.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class NodeExecutorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutorRegistry.class);

    private final Map<String, NodeExecutor<?, ?>> executors = new HashMap<>();

    /**
     * Constructs the registry and auto-registers all NodeExecutor beans.
     *
     * Spring automatically injects all beans implementing NodeExecutor. Each executor's
     * node type is extracted and registered. Duplicate types cause IllegalStateException.
     *
     * @param executorBeans list of all NodeExecutor bean instances discovered by Spring
     * @throws IllegalStateException if two executors return the same node type
     * @throws IllegalArgumentException if executor returns null/blank node type
     */
    public NodeExecutorRegistry(List<NodeExecutor<?, ?>> executorBeans) {
        logger.debug("Initializing NodeExecutorRegistry with {} executor beans", executorBeans.size());

        for (NodeExecutor<?, ?> executor : executorBeans) {
            String nodeType = executor.getNodeType();

            if (nodeType == null || nodeType.trim().isEmpty()) {
                logger.error("Executor {} returned null or empty node type", executor.getClass().getSimpleName());
                throw new IllegalArgumentException(
                    "Executor " + executor.getClass().getName() + " returned null or empty node type"
                );
            }

            String trimmedType = nodeType.trim();

            if (executors.containsKey(trimmedType)) {
                String existingClass = executors.get(trimmedType).getClass().getSimpleName();
                String duplicateClass = executor.getClass().getSimpleName();
                logger.error(
                    "Duplicate executor registration for nodeType='{}'. Existing: {}, Duplicate: {}",
                    trimmedType, existingClass, duplicateClass
                );
                throw new IllegalStateException(
                    "Duplicate executor registration for nodeType='" + trimmedType + "'. " +
                    "Existing: " + existingClass + ", Duplicate: " + duplicateClass
                );
            }

            executors.put(trimmedType, executor);
            logger.debug("Registered executor for node type '{}': {}", trimmedType, executor.getClass().getSimpleName());
        }

        logRegistrySummary();
    }

    /**
     * Normalizes a node type string by trimming whitespace.
     *
     * Handles null inputs safely by returning empty string.
     *
     * @param nodeType the node type to normalize
     * @return trimmed node type, or empty string if input is null
     */
    private String normalize(String nodeType) {
        return nodeType == null ? "" : nodeType.trim();
    }

    /**
     * Logs a summary of all registered executors at startup.
     *
     * Displays the complete list of registered node types and their executor classes.
     */
    private void logRegistrySummary() {
        List<String> sortedKeys = executors.keySet().stream()
            .sorted()
            .collect(Collectors.toList());

        logger.info("NodeExecutorRegistry initialized with {} registered executors", executors.size());
        logger.info("Registered node types: {}", sortedKeys);

        for (String nodeType : sortedKeys) {
            String executorClass = executors.get(nodeType).getClass().getSimpleName();
            logger.debug("Node type '{}' -> {}", nodeType, executorClass);
        }
    }

    /**
     * Registers a node executor in the registry.
     *
     * Maps the executor's node type identifier to the executor instance. Useful for
     * runtime registration after initialization (e.g., custom executors).
     *
     * @param executor the executor to register
     * @throws IllegalStateException if the node type is already registered
     */
    public void register(NodeExecutor<?, ?> executor) {
        String nodeType = executor.getNodeType();

        if (nodeType == null || nodeType.trim().isEmpty()) {
            logger.error("Cannot register executor {} with null/empty node type", executor.getClass().getSimpleName());
            throw new IllegalArgumentException("Node type cannot be null or empty");
        }

        String trimmedType = nodeType.trim();

        if (executors.containsKey(trimmedType)) {
            String existingClass = executors.get(trimmedType).getClass().getSimpleName();
            logger.error(
                "Cannot register executor for nodeType='{}' - already registered to {}",
                trimmedType, existingClass
            );
            throw new IllegalStateException(
                "Executor already registered for nodeType='" + trimmedType + "'"
            );
        }

        executors.put(trimmedType, executor);
        logger.info("Runtime registered executor for node type '{}': {}", trimmedType, executor.getClass().getSimpleName());
    }

    /**
     * Retrieves an executor by its node type identifier.
     *
     * @param nodeType the node type identifier to lookup
     * @return the executor instance for the given node type
     * @throws IllegalArgumentException if nodeType is null/blank or no executor is registered
     */
    public NodeExecutor<?, ?> getExecutor(String nodeType) {
        String normalized = normalize(nodeType);

        if (normalized.isEmpty()) {
            List<String> available = executors.keySet().stream().sorted().collect(Collectors.toList());
            logger.error("Node type cannot be null or blank. Available types: {}", available);
            throw new IllegalArgumentException(
                "Node type cannot be null or blank. Available types: " + available
            );
        }

        logger.debug("Looking up executor for node type: {}", normalized);
        NodeExecutor<?, ?> executor = executors.get(normalized);
        if (executor == null) {
            List<String> available = executors.keySet().stream().sorted().collect(Collectors.toList());
            logger.error(
                "No executor registered for nodeType='{}'. Available types: {}",
                normalized, available
            );
            throw new IllegalArgumentException(
                "No executor registered for nodeType='" + normalized + "'. Available: " + available
            );
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
        String normalized = normalize(nodeType);
        boolean exists = executors.containsKey(normalized);
        if (!exists) {
            logger.debug("No executor found for node type: {}", normalized);
        }
        return exists;
    }

    /**
     * Unregisters (removes) an executor from the registry.
     *
     * @param nodeType the node type identifier whose executor should be removed
     */
    public void unregister(String nodeType) {
        String normalized = normalize(nodeType);
        if (executors.remove(normalized) != null) {
            logger.debug("Unregistered executor for node type: {}", normalized);
        } else {
            logger.warn("Attempted to unregister non-existent executor for node type: {}", normalized);
        }
    }

    /**
     * Returns the total count of registered executors.
     *
     * @return number of registered executor types
     */
    public int getExecutorCount() {
        return executors.size();
    }
}
