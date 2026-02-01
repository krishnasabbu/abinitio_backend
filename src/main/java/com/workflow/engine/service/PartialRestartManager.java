package com.workflow.engine.service;

import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.GraphValidationException;
import com.workflow.engine.graph.StepKind;
import com.workflow.engine.graph.StepNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PartialRestartManager {

    private static final Logger logger = LoggerFactory.getLogger(PartialRestartManager.class);

    public ExecutionPlan createPartialPlan(ExecutionPlan originalPlan, String fromNodeId) {
        Objects.requireNonNull(originalPlan, "Original plan cannot be null");
        Objects.requireNonNull(fromNodeId, "fromNodeId cannot be null");

        StepNode fromNode = originalPlan.steps().get(fromNodeId);
        if (fromNode == null) {
            throw new GraphValidationException(String.format(
                "Cannot restart from node '%s': node does not exist in plan", fromNodeId));
        }

        logger.info("[RESTART] Creating partial plan from node '{}' (kind={})", fromNodeId, fromNode.kind());

        Set<String> reachableNodes = findReachableNodes(originalPlan, fromNodeId);
        logger.debug("[RESTART] Reachable nodes from '{}': {}", fromNodeId, reachableNodes);

        Map<String, StepNode> partialSteps = new LinkedHashMap<>();
        for (String nodeId : reachableNodes) {
            StepNode node = originalPlan.steps().get(nodeId);
            if (node != null) {
                StepNode filteredNode = filterUnreachableReferences(node, reachableNodes);
                partialSteps.put(nodeId, filteredNode);
            }
        }

        List<String> newEntryPoints = List.of(fromNodeId);

        StepNode updatedFromNode = partialSteps.get(fromNodeId);
        if (updatedFromNode != null && updatedFromNode.upstreamSteps() != null) {
            updatedFromNode = new StepNode(
                updatedFromNode.nodeId(),
                updatedFromNode.nodeType(),
                updatedFromNode.config(),
                updatedFromNode.nextSteps(),
                updatedFromNode.errorSteps(),
                updatedFromNode.metrics(),
                updatedFromNode.exceptionHandling(),
                updatedFromNode.executionHints(),
                updatedFromNode.classification(),
                updatedFromNode.outputPorts(),
                updatedFromNode.kind() == StepKind.JOIN ? StepKind.NORMAL : updatedFromNode.kind(),
                null
            );
            partialSteps.put(fromNodeId, updatedFromNode);
        }

        validatePartialPlan(partialSteps, fromNodeId);

        logger.info("[RESTART] Partial plan created with {} steps (original: {})",
            partialSteps.size(), originalPlan.steps().size());

        return new ExecutionPlan(newEntryPoints, partialSteps, originalPlan.workflowId() + "_restart");
    }

    public ExecutionPlan createPartialPlanFromFailedNodes(
            ExecutionPlan originalPlan,
            JdbcTemplate jdbcTemplate,
            String originalExecutionId) {

        String sql = "SELECT node_id FROM node_executions WHERE execution_id = ? AND status = 'failed' ORDER BY start_time";
        List<String> failedNodes = jdbcTemplate.queryForList(sql, String.class, originalExecutionId);

        if (failedNodes.isEmpty()) {
            logger.info("[RESTART] No failed nodes found for execution '{}', returning original plan",
                originalExecutionId);
            return originalPlan;
        }

        logger.info("[RESTART] Found {} failed nodes: {}", failedNodes.size(), failedNodes);

        Set<String> completedNodes = getCompletedNodes(jdbcTemplate, originalExecutionId);

        Set<String> nodesToRerun = new LinkedHashSet<>();
        for (String failedNode : failedNodes) {
            nodesToRerun.add(failedNode);
            Set<String> downstream = findReachableNodes(originalPlan, failedNode);
            nodesToRerun.addAll(downstream);
        }

        nodesToRerun.removeAll(completedNodes);
        nodesToRerun.addAll(failedNodes);

        logger.info("[RESTART] Nodes to rerun: {}", nodesToRerun);

        return createPartialPlanForNodes(originalPlan, nodesToRerun, failedNodes);
    }

    private Set<String> getCompletedNodes(JdbcTemplate jdbcTemplate, String executionId) {
        String sql = "SELECT node_id FROM node_executions WHERE execution_id = ? AND status = 'success'";
        List<String> completed = jdbcTemplate.queryForList(sql, String.class, executionId);
        return new HashSet<>(completed);
    }

    private ExecutionPlan createPartialPlanForNodes(
            ExecutionPlan originalPlan,
            Set<String> nodesToInclude,
            List<String> entryPoints) {

        Map<String, StepNode> partialSteps = new LinkedHashMap<>();

        for (String nodeId : nodesToInclude) {
            StepNode node = originalPlan.steps().get(nodeId);
            if (node != null) {
                StepNode filteredNode = filterUnreachableReferences(node, nodesToInclude);
                partialSteps.put(nodeId, filteredNode);
            }
        }

        List<String> validEntryPoints = entryPoints.stream()
            .filter(partialSteps::containsKey)
            .collect(Collectors.toList());

        if (validEntryPoints.isEmpty() && !partialSteps.isEmpty()) {
            validEntryPoints = List.of(partialSteps.keySet().iterator().next());
        }

        return new ExecutionPlan(validEntryPoints, partialSteps, originalPlan.workflowId() + "_restart");
    }

    private Set<String> findReachableNodes(ExecutionPlan plan, String fromNodeId) {
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(fromNodeId);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (reachable.contains(nodeId)) {
                continue;
            }
            reachable.add(nodeId);

            StepNode node = plan.steps().get(nodeId);
            if (node == null) {
                continue;
            }

            if (node.nextSteps() != null) {
                for (String nextId : node.nextSteps()) {
                    if (!reachable.contains(nextId) && plan.steps().containsKey(nextId)) {
                        queue.add(nextId);
                    }
                }
            }

            if (node.errorSteps() != null) {
                for (String errorId : node.errorSteps()) {
                    if (!reachable.contains(errorId) && plan.steps().containsKey(errorId)) {
                        queue.add(errorId);
                    }
                }
            }
        }

        return reachable;
    }

    private StepNode filterUnreachableReferences(StepNode node, Set<String> reachableNodes) {
        List<String> filteredNextSteps = null;
        if (node.nextSteps() != null) {
            filteredNextSteps = node.nextSteps().stream()
                .filter(reachableNodes::contains)
                .collect(Collectors.toList());
            if (filteredNextSteps.isEmpty()) {
                filteredNextSteps = null;
            }
        }

        List<String> filteredErrorSteps = null;
        if (node.errorSteps() != null) {
            filteredErrorSteps = node.errorSteps().stream()
                .filter(reachableNodes::contains)
                .collect(Collectors.toList());
            if (filteredErrorSteps.isEmpty()) {
                filteredErrorSteps = null;
            }
        }

        List<String> filteredUpstreamSteps = null;
        if (node.upstreamSteps() != null) {
            filteredUpstreamSteps = node.upstreamSteps().stream()
                .filter(reachableNodes::contains)
                .collect(Collectors.toList());
            if (filteredUpstreamSteps.isEmpty()) {
                filteredUpstreamSteps = null;
            }
        }

        if (Objects.equals(node.nextSteps(), filteredNextSteps) &&
            Objects.equals(node.errorSteps(), filteredErrorSteps) &&
            Objects.equals(node.upstreamSteps(), filteredUpstreamSteps)) {
            return node;
        }

        return new StepNode(
            node.nodeId(),
            node.nodeType(),
            node.config(),
            filteredNextSteps,
            filteredErrorSteps,
            node.metrics(),
            node.exceptionHandling(),
            node.executionHints(),
            node.classification(),
            node.outputPorts(),
            node.kind(),
            filteredUpstreamSteps
        );
    }

    private void validatePartialPlan(Map<String, StepNode> steps, String fromNodeId) {
        for (Map.Entry<String, StepNode> entry : steps.entrySet()) {
            StepNode node = entry.getValue();

            if (node.kind() == StepKind.FORK) {
                String joinNodeId = node.executionHints() != null
                    ? node.executionHints().getJoinNodeId()
                    : null;

                if (joinNodeId != null && !steps.containsKey(joinNodeId)) {
                    throw new GraphValidationException(String.format(
                        "Partial restart from '%s' includes FORK '%s' but its JOIN '%s' is not reachable. " +
                        "Cannot partially restart mid-fork. Choose a node before the FORK or after the JOIN.",
                        fromNodeId, node.nodeId(), joinNodeId));
                }
            }
        }
    }

    public static class RestartContext {
        private final String originalExecutionId;
        private final String fromNodeId;
        private final Map<String, Object> preservedState;

        public RestartContext(String originalExecutionId, String fromNodeId) {
            this.originalExecutionId = originalExecutionId;
            this.fromNodeId = fromNodeId;
            this.preservedState = new HashMap<>();
        }

        public String getOriginalExecutionId() {
            return originalExecutionId;
        }

        public String getFromNodeId() {
            return fromNodeId;
        }

        public Map<String, Object> getPreservedState() {
            return preservedState;
        }

        public void preserveState(String key, Object value) {
            preservedState.put(key, value);
        }
    }
}
