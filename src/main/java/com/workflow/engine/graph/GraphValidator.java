package com.workflow.engine.graph;

import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.model.Edge;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GraphValidator {

    private final NodeExecutorRegistry executorRegistry;

    public GraphValidator(NodeExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    public ValidationResult validate(WorkflowDefinition workflow) {
        ValidationResult result = new ValidationResult();

        validateNodes(workflow, result);
        validateEdges(workflow, result);
        validateNoCycles(workflow, result);
        validateExecutors(workflow, result);
        validateSourceNodes(workflow, result);

        return result;
    }

    private void validateNodes(WorkflowDefinition workflow, ValidationResult result) {
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

    private void validateEdges(WorkflowDefinition workflow, ValidationResult result) {
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

    private void validateNoCycles(WorkflowDefinition workflow, ValidationResult result) {
        try {
            if (DagUtils.hasCycle(workflow)) {
                result.addError("Workflow contains a cycle");
            }
        } catch (Exception e) {
            result.addError("Error checking for cycles: " + e.getMessage());
        }
    }

    private void validateExecutors(WorkflowDefinition workflow, ValidationResult result) {
        for (NodeDefinition node : workflow.getNodes()) {
            if ("Start".equalsIgnoreCase(node.getType())) {
                continue;
            }

            if (!executorRegistry.hasExecutor(node.getType())) {
                result.addWarning("No executor registered for node type: " + node.getType());
            }
        }
    }

    private void validateSourceNodes(WorkflowDefinition workflow, ValidationResult result) {
        List<String> sourceNodes = DagUtils.findSourceNodes(workflow);
        if (sourceNodes.isEmpty()) {
            result.addError("Workflow must have at least one source node");
        }
    }

    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

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
