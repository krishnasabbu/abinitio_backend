package com.workflow.engine.graph;

import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.model.Edge;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.WorkflowDefinition;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Validates workflow definitions for correctness and consistency.
 *
 * Performs comprehensive validation of workflow definitions including:
 * - Node structure and uniqueness
 * - Edge connectivity and references
 * - Absence of cycles (must be a valid DAG)
 * - Executor availability for all node types
 * - Presence of source nodes
 *
 * Returns a ValidationResult containing all detected errors and warnings.
 * Use isValid() to determine if the workflow can be executed.
 *
 * Thread safety: Thread-safe. No mutable state beyond constructor dependencies.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see ValidationResult
 */
@Component
public class GraphValidator {

    private static final Logger logger = LoggerFactory.getLogger(GraphValidator.class);

    private final NodeExecutorRegistry executorRegistry;

    /**
     * Constructs a GraphValidator with the provided executor registry.
     *
     * @param executorRegistry the registry for looking up node executors
     */
    public GraphValidator(NodeExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    /**
     * Performs comprehensive validation of a workflow definition.
     *
     * Checks all aspects of the workflow structure and configuration.
     * Returns detailed validation results including all errors and warnings found.
     *
     * @param workflow the workflow definition to validate
     * @return ValidationResult containing errors and warnings (use isValid() to check)
     */
    public ValidationResult validate(WorkflowDefinition workflow) {
        logger.debug("Starting validation of workflow: {}", workflow.getName());

        ValidationResult result = new ValidationResult();

        validateNodes(workflow, result);
        validateEdges(workflow, result);
        validateNoCycles(workflow, result);
        validateExecutors(workflow, result);
        validateSourceNodes(workflow, result);

        if (result.isValid()) {
            logger.info("Workflow validation successful: {}", workflow.getName());
        } else {
            logger.error("Workflow validation failed: {} with {} errors", workflow.getName(), result.getErrors().size());
        }

        return result;
    }

    /**
     * Validates that all nodes are properly defined and unique.
     *
     * @param workflow the workflow to validate
     * @param result the result object to add errors to
     */
    private void validateNodes(WorkflowDefinition workflow, ValidationResult result) {
        logger.debug("Validating workflow nodes");
        if (workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            result.addError("Workflow must contain at least one node");
            return;
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : workflow.getNodes()) {
            if (node.getId() == null || node.getId().isEmpty()) {
                result.addError("Node must have an ID");
            } else if (nodeIds.contains(node.getId())) {
                result.addError("Duplicate node ID: " + node.getId());
            } else {
                nodeIds.add(node.getId());
            }

            if (node.getType() == null || node.getType().isEmpty()) {
                result.addError("Node " + node.getId() + " must have a type");
            }
        }
    }

    /**
     * Validates that all edges reference existing nodes.
     *
     * @param workflow the workflow to validate
     * @param result the result object to add errors to
     */
    private void validateEdges(WorkflowDefinition workflow, ValidationResult result) {
        logger.debug("Validating workflow edges");
        if (workflow.getEdges() == null) {
            return;
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : workflow.getNodes()) {
            nodeIds.add(node.getId());
        }

        for (Edge edge : workflow.getEdges()) {
            if (edge.getSource() == null || edge.getSource().isEmpty()) {
                result.addError("Edge must have a source");
            } else if (!nodeIds.contains(edge.getSource())) {
                result.addError("Edge source not found: " + edge.getSource());
            }

            if (edge.getTarget() == null || edge.getTarget().isEmpty()) {
                result.addError("Edge must have a target");
            } else if (!nodeIds.contains(edge.getTarget())) {
                result.addError("Edge target not found: " + edge.getTarget());
            }
        }
    }

    /**
     * Validates that the workflow is acyclic (no circular dependencies).
     *
     * @param workflow the workflow to validate
     * @param result the result object to add errors to
     */
    private void validateNoCycles(WorkflowDefinition workflow, ValidationResult result) {
        logger.debug("Checking for cycles in workflow");
        try {
            if (DagUtils.hasCycle(workflow)) {
                result.addError("Workflow contains a cycle");
            }
        } catch (Exception e) {
            result.addError("Error checking for cycles: " + e.getMessage());
        }
    }

    /**
     * Validates that executors are registered for all required node types.
     *
     * @param workflow the workflow to validate
     * @param result the result object to add warnings to
     */
    private void validateExecutors(WorkflowDefinition workflow, ValidationResult result) {
        logger.debug("Validating executor availability for all node types");
        for (NodeDefinition node : workflow.getNodes()) {
            if ("Start".equalsIgnoreCase(node.getType())) {
                continue;
            }

            if (!executorRegistry.hasExecutor(node.getType())) {
                result.addWarning("No executor registered for node type: " + node.getType());
            }
        }
    }

    /**
     * Validates that the workflow has at least one source node.
     *
     * @param workflow the workflow to validate
     * @param result the result object to add errors to
     */
    private void validateSourceNodes(WorkflowDefinition workflow, ValidationResult result) {
        logger.debug("Validating source nodes in workflow");
        List<String> sourceNodes = DagUtils.findSourceNodes(workflow);
        if (sourceNodes.isEmpty()) {
            result.addError("Workflow must have at least one source node");
        }
    }

    /**
     * Contains the results of workflow validation.
     *
     * Holds validation errors (which prevent execution) and warnings (which indicate
     * potential issues but don't prevent execution). Errors must be empty for the
     * workflow to be considered valid.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        /**
         * Adds a validation error to the result.
         *
         * Errors indicate validation failures that prevent workflow execution.
         *
         * @param error the error message to add
         */
        public void addError(String error) {
            errors.add(error);
        }

        /**
         * Adds a validation warning to the result.
         *
         * Warnings indicate potential issues that don't prevent execution but should
         * be reviewed.
         *
         * @param warning the warning message to add
         */
        public void addWarning(String warning) {
            warnings.add(warning);
        }

        /**
         * Checks if the validation was successful (no errors).
         *
         * @return true if there are no errors, false otherwise
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Retrieves all validation errors.
         *
         * @return unmodifiable list of error messages
         */
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        /**
         * Retrieves all validation warnings.
         *
         * @return unmodifiable list of warning messages
         */
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
            }
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
            }
            return sb.toString();
        }
    }
}
