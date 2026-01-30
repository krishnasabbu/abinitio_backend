package com.workflow.engine.graph;

import com.workflow.engine.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExecutionGraphBuilder {

    public ExecutionPlan build(WorkflowDefinition workflow) {
        List<NodeDefinition> nodes = workflow.getNodes();
        List<Edge> edges = workflow.getEdges();

        validateGraph(nodes, edges);

        List<String> entryStepIds = resolveEntryNodes(nodes, edges);

        Map<String, List<String>> dataAdjacency = buildDataAdjacency(edges);
        Map<String, List<String>> controlAdjacency = buildControlAdjacency(edges);

        Map<String, StepNode> steps = new LinkedHashMap<>();

        for (NodeDefinition node : nodes) {
            if ("Start".equals(node.getType())) {
                continue;
            }

            StepClassification classification = classifyNode(node, dataAdjacency, edges);

            List<String> nextSteps = determineNextSteps(node.getId(), dataAdjacency, controlAdjacency, classification);
            List<String> errorSteps = determineErrorSteps(node.getId(), edges, nodes);

            StepNode stepNode = new StepNode(
                node.getId(),
                node.getType(),
                node.getConfig(),
                nextSteps,
                errorSteps,
                node.getMetrics() != null ? node.getMetrics() : new MetricsConfig(),
                node.getOnFailure() != null ? node.getOnFailure() : new FailurePolicy(),
                node.getExecutionHints() != null ? node.getExecutionHints() : new ExecutionHints(),
                classification
            );

            steps.put(node.getId(), stepNode);
        }

        return new ExecutionPlan(entryStepIds, steps);
    }

    private void validateGraph(List<NodeDefinition> nodes, List<Edge> edges) {
        long startNodeCount = nodes.stream()
            .filter(n -> "Start".equals(n.getType()))
            .count();

        if (startNodeCount == 0) {
            throw new GraphValidationException("Workflow must have exactly one Start node");
        }
        if (startNodeCount > 1) {
            throw new GraphValidationException("Workflow cannot have multiple Start nodes");
        }

        if (hasCycles(nodes, edges)) {
            throw new GraphValidationException("Workflow graph contains cycles");
        }

        validateJoinNodes(nodes, edges);
        validateSinkNodes(nodes, edges);
        validateControlOnlyNodes(nodes, edges);
        validateEdgeCompatibility(edges, nodes);
    }

    private boolean hasCycles(List<NodeDefinition> nodes, List<Edge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();

        for (Edge edge : edges) {
            if (!edge.isControl()) {
                adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (NodeDefinition node : nodes) {
            if (hasCyclesDFS(node.getId(), adjacency, visited, recStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCyclesDFS(String nodeId, Map<String, List<String>> adjacency,
                                  Set<String> visited, Set<String> recStack) {
        if (recStack.contains(nodeId)) {
            return true;
        }

        if (visited.contains(nodeId)) {
            return false;
        }

        visited.add(nodeId);
        recStack.add(nodeId);

        List<String> neighbors = adjacency.getOrDefault(nodeId, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (hasCyclesDFS(neighbor, adjacency, visited, recStack)) {
                return true;
            }
        }

        recStack.remove(nodeId);
        return false;
    }

    private void validateJoinNodes(List<NodeDefinition> nodes, List<Edge> edges) {
        for (NodeDefinition node : nodes) {
            if (isJoinNodeType(node.getType())) {
                long dataInputCount = edges.stream()
                    .filter(e -> !e.isControl())
                    .filter(e -> e.getTarget().equals(node.getId()))
                    .count();

                if (dataInputCount < 2) {
                    throw new GraphValidationException(
                        "Join node '" + node.getId() + "' must have at least 2 data inputs, found: " + dataInputCount
                    );
                }
            }
        }
    }

    private void validateSinkNodes(List<NodeDefinition> nodes, List<Edge> edges) {
        for (NodeDefinition node : nodes) {
            if (isSinkNodeType(node.getType())) {
                boolean hasOutgoingEdge = edges.stream()
                    .anyMatch(e -> e.getSource().equals(node.getId()) && !e.isControl());

                if (hasOutgoingEdge) {
                    throw new GraphValidationException(
                        "Sink node '" + node.getId() + "' cannot have outgoing data edges"
                    );
                }
            }
        }
    }

    private void validateControlOnlyNodes(List<NodeDefinition> nodes, List<Edge> edges) {
        for (NodeDefinition node : nodes) {
            if ("Start".equals(node.getType())) {
                boolean hasDataEdge = edges.stream()
                    .filter(e -> e.getSource().equals(node.getId()))
                    .anyMatch(e -> !e.isControl());

                if (hasDataEdge) {
                    throw new GraphValidationException(
                        "Control-only node '" + node.getId() + "' cannot have data edges"
                    );
                }
            }
        }
    }

    private void validateEdgeCompatibility(List<Edge> edges, List<NodeDefinition> nodes) {
        Map<String, NodeDefinition> nodeMap = nodes.stream()
            .collect(Collectors.toMap(NodeDefinition::getId, n -> n));

        for (Edge edge : edges) {
            NodeDefinition source = nodeMap.get(edge.getSource());
            NodeDefinition target = nodeMap.get(edge.getTarget());

            if (source == null || target == null) {
                continue;
            }
        }
    }

    private List<String> resolveEntryNodes(List<NodeDefinition> nodes, List<Edge> edges) {
        NodeDefinition startNode = nodes.stream()
            .filter(n -> "Start".equals(n.getType()))
            .findFirst()
            .orElseThrow(() -> new GraphValidationException("No Start node found"));

        List<String> entryNodes = edges.stream()
            .filter(Edge::isControl)
            .filter(e -> e.getSource().equals(startNode.getId()))
            .map(Edge::getTarget)
            .collect(Collectors.toList());

        if (entryNodes.isEmpty()) {
            throw new GraphValidationException("Start node must have at least one outgoing control edge");
        }

        return entryNodes;
    }

    private StepClassification classifyNode(NodeDefinition node, Map<String, List<String>> dataAdjacency,
                                             List<Edge> edges) {
        String type = node.getType();

        long dataInputs = edges.stream()
            .filter(e -> !e.isControl())
            .filter(e -> e.getTarget().equals(node.getId()))
            .count();

        long dataOutputs = dataAdjacency.getOrDefault(node.getId(), Collections.emptyList()).size();

        if (dataInputs == 0 && dataOutputs > 0) {
            return StepClassification.SOURCE;
        }

        if (dataOutputs == 0 && dataInputs > 0) {
            return StepClassification.SINK;
        }

        if (dataInputs == 1 && dataOutputs > 1) {
            return StepClassification.SPLIT;
        }

        if (dataInputs > 1 && dataOutputs == 1) {
            return StepClassification.JOIN;
        }

        if (dataInputs == 1 && dataOutputs == 1) {
            return StepClassification.TRANSFORM;
        }

        return StepClassification.CONTROL;
    }

    private Map<String, List<String>> buildDataAdjacency(List<Edge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();

        for (Edge edge : edges) {
            if (!edge.isControl()) {
                adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
            }
        }

        return adjacency;
    }

    private Map<String, List<String>> buildControlAdjacency(List<Edge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();

        for (Edge edge : edges) {
            if (edge.isControl()) {
                adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
            }
        }

        return adjacency;
    }

    private List<String> determineNextSteps(String nodeId, Map<String, List<String>> dataAdjacency,
                                            Map<String, List<String>> controlAdjacency,
                                            StepClassification classification) {
        Set<String> nextSteps = new LinkedHashSet<>();

        nextSteps.addAll(dataAdjacency.getOrDefault(nodeId, Collections.emptyList()));
        nextSteps.addAll(controlAdjacency.getOrDefault(nodeId, Collections.emptyList()));

        return new ArrayList<>(nextSteps);
    }

    private List<String> determineErrorSteps(String nodeId, List<Edge> edges, List<NodeDefinition> nodes) {
        Map<String, String> nodeTypes = nodes.stream()
            .collect(Collectors.toMap(NodeDefinition::getId, NodeDefinition::getType));

        return edges.stream()
            .filter(e -> e.getSource().equals(nodeId))
            .map(Edge::getTarget)
            .filter(targetId -> {
                String type = nodeTypes.get(targetId);
                return "Reject".equals(type) || "ErrorSink".equals(type);
            })
            .collect(Collectors.toList());
    }

    private boolean isJoinNodeType(String type) {
        return "Join".equals(type);
    }

    private boolean isSinkNodeType(String type) {
        return "DBSink".equals(type) ||
               "FileSink".equals(type) ||
               "ErrorSink".equals(type);
    }
}
