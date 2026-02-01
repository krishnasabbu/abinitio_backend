package com.workflow.engine.graph;

import com.workflow.engine.execution.routing.OutputPort;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.model.*;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExecutionGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionGraphBuilder.class);
    private final NodeExecutorRegistry nodeExecutorRegistry;

    public ExecutionGraphBuilder(NodeExecutorRegistry nodeExecutorRegistry) {
        this.nodeExecutorRegistry = nodeExecutorRegistry;
    }

    private String normalize(String nodeType) {
        return nodeType == null ? "" : nodeType.trim();
    }

    public ExecutionPlan build(WorkflowDefinition workflow) {
        List<NodeDefinition> nodes = workflow.getNodes();
        List<Edge> edges = workflow.getEdges();

        validateGraph(nodes, edges);

        List<String> entryStepIds = resolveEntryNodes(nodes, edges);

        Map<String, List<String>> dataAdjacency = buildDataAdjacency(edges);
        Map<String, List<String>> controlAdjacency = buildControlAdjacency(edges);
        Map<String, List<String>> reverseDataAdjacency = buildReverseDataAdjacency(edges);

        Map<String, StepNode> steps = new LinkedHashMap<>();

        Map<String, NodeDefinition> nodeMap = nodes.stream()
            .collect(Collectors.toMap(NodeDefinition::getId, n -> n));

        for (NodeDefinition node : nodes) {
            if ("Start".equals(node.getType())) {
                continue;
            }

            StepClassification classification = classifyNode(node, dataAdjacency, edges);

            List<String> nextSteps = determineNextSteps(node.getId(), dataAdjacency, controlAdjacency, classification);
            List<String> errorSteps = determineErrorSteps(node.getId(), edges, nodes);
            List<OutputPort> outputPorts = determineOutputPorts(node.getId(), edges);
            List<String> upstreamSteps = determineUpstreamSteps(node.getId(), reverseDataAdjacency);

            StepKind kind = determineStepKind(node, nextSteps, upstreamSteps, classification);

            ExecutionHints hints = node.getExecutionHints() != null
                ? node.getExecutionHints()
                : new ExecutionHints();

            if (kind == StepKind.FORK && nextSteps.size() > 1) {
                String joinNodeId = findConvergenceJoin(node.getId(), nextSteps, dataAdjacency, nodeMap, reverseDataAdjacency);
                if (joinNodeId != null && hints.getJoinNodeId() == null) {
                    hints.setJoinNodeId(joinNodeId);
                    logger.debug("Inferred joinNodeId='{}' for FORK node '{}'", joinNodeId, node.getId());
                }
            }

            StepNode stepNode = new StepNode(
                node.getId(),
                node.getType(),
                node.getConfig(),
                nextSteps,
                errorSteps,
                node.getMetrics() != null ? node.getMetrics() : new MetricsConfig(),
                node.getOnFailure() != null ? node.getOnFailure() : new FailurePolicy(),
                hints,
                classification,
                outputPorts,
                kind,
                upstreamSteps
            );

            steps.put(node.getId(), stepNode);
        }

        validateForkJoinSemantics(steps);

        String workflowId = workflow.getId() != null ? workflow.getId() : "workflow-" + UUID.randomUUID();
        return new ExecutionPlan(entryStepIds, steps, workflowId);
    }

    private StepKind determineStepKind(NodeDefinition node, List<String> nextSteps,
                                       List<String> upstreamSteps, StepClassification classification) {
        String nodeType = node.getType();

        if ("Join".equals(nodeType) || "Gather".equals(nodeType) || "Collect".equals(nodeType) ||
            "Merge".equals(nodeType) || "Intersect".equals(nodeType) || "Minus".equals(nodeType)) {
            return StepKind.JOIN;
        }

        if ("Barrier".equals(nodeType) || "JoinBarrier".equals(nodeType)) {
            return StepKind.BARRIER;
        }

        if ("Decision".equals(nodeType) || "Switch".equals(nodeType) || "JobCondition".equals(nodeType)) {
            return StepKind.DECISION;
        }

        if ("Subgraph".equals(nodeType)) {
            return StepKind.SUBGRAPH;
        }

        if (upstreamSteps != null && upstreamSteps.size() > 1 && classification == StepClassification.JOIN) {
            return StepKind.JOIN;
        }

        boolean hasMultipleOutputs = nextSteps != null && nextSteps.size() > 1;
        boolean isParallelMode = node.getExecutionHints() != null &&
            node.getExecutionHints().getMode() == ExecutionMode.PARALLEL;

        if ("Split".equals(nodeType) || "Replicate".equals(nodeType) ||
            "Partition".equals(nodeType) || "HashPartition".equals(nodeType) ||
            "RangePartition".equals(nodeType) || "Broadcast".equals(nodeType)) {
            return StepKind.FORK;
        }

        if (hasMultipleOutputs && isParallelMode) {
            return StepKind.FORK;
        }

        return StepKind.NORMAL;
    }

    private List<String> determineUpstreamSteps(String nodeId, Map<String, List<String>> reverseDataAdjacency) {
        return reverseDataAdjacency.getOrDefault(nodeId, Collections.emptyList());
    }

    private Map<String, List<String>> buildReverseDataAdjacency(List<Edge> edges) {
        Map<String, List<String>> reverse = new HashMap<>();
        for (Edge edge : edges) {
            if (!edge.isControl()) {
                reverse.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge.getSource());
            }
        }
        return reverse;
    }

    private String findConvergenceJoin(String forkNodeId, List<String> branches,
                                       Map<String, List<String>> dataAdjacency,
                                       Map<String, NodeDefinition> nodeMap,
                                       Map<String, List<String>> reverseDataAdjacency) {
        if (branches == null || branches.size() < 2) {
            return null;
        }

        Set<String> commonDescendants = null;

        for (String branchStart : branches) {
            Set<String> branchDescendants = collectAllDescendants(branchStart, dataAdjacency, new HashSet<>());
            if (commonDescendants == null) {
                commonDescendants = new HashSet<>(branchDescendants);
            } else {
                commonDescendants.retainAll(branchDescendants);
            }
        }

        if (commonDescendants == null || commonDescendants.isEmpty()) {
            return null;
        }

        for (String candidate : commonDescendants) {
            List<String> upstream = reverseDataAdjacency.getOrDefault(candidate, Collections.emptyList());
            if (upstream.size() >= branches.size()) {
                NodeDefinition candidateNode = nodeMap.get(candidate);
                if (candidateNode != null) {
                    String type = candidateNode.getType();
                    if ("Join".equals(type) || "Gather".equals(type) || "Collect".equals(type) ||
                        "Merge".equals(type) || "Barrier".equals(type)) {
                        return candidate;
                    }
                }
            }
        }

        String firstCommon = null;
        int minDepth = Integer.MAX_VALUE;
        for (String candidate : commonDescendants) {
            int depth = computeMinDepthFromBranches(candidate, branches, dataAdjacency);
            if (depth < minDepth) {
                minDepth = depth;
                firstCommon = candidate;
            }
        }

        return firstCommon;
    }

    private Set<String> collectAllDescendants(String nodeId, Map<String, List<String>> adjacency, Set<String> visited) {
        Set<String> descendants = new HashSet<>();
        if (visited.contains(nodeId)) {
            return descendants;
        }
        visited.add(nodeId);

        List<String> nexts = adjacency.getOrDefault(nodeId, Collections.emptyList());
        for (String next : nexts) {
            descendants.add(next);
            descendants.addAll(collectAllDescendants(next, adjacency, visited));
        }
        return descendants;
    }

    private int computeMinDepthFromBranches(String target, List<String> sources, Map<String, List<String>> adjacency) {
        int maxDepth = 0;
        for (String source : sources) {
            int depth = computeDepth(source, target, adjacency, new HashSet<>(), 0);
            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }
        return maxDepth;
    }

    private int computeDepth(String from, String to, Map<String, List<String>> adjacency, Set<String> visited, int depth) {
        if (from.equals(to)) {
            return depth;
        }
        if (visited.contains(from)) {
            return Integer.MAX_VALUE;
        }
        visited.add(from);

        List<String> nexts = adjacency.getOrDefault(from, Collections.emptyList());
        int minDepth = Integer.MAX_VALUE;
        for (String next : nexts) {
            int d = computeDepth(next, to, adjacency, new HashSet<>(visited), depth + 1);
            if (d < minDepth) {
                minDepth = d;
            }
        }
        return minDepth;
    }

    private void validateForkJoinSemantics(Map<String, StepNode> steps) {
        for (Map.Entry<String, StepNode> entry : steps.entrySet()) {
            String nodeId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.kind() == StepKind.FORK) {
                List<String> nextSteps = node.nextSteps();
                if (nextSteps != null && nextSteps.size() > 1) {
                    String joinNodeId = node.executionHints() != null
                        ? node.executionHints().getJoinNodeId()
                        : null;

                    if (joinNodeId == null) {
                        logger.warn("FORK node '{}' has {} branches but no explicit joinNodeId. " +
                            "This may cause duplicate downstream execution.", nodeId, nextSteps.size());
                    } else {
                        StepNode joinNode = steps.get(joinNodeId);
                        if (joinNode == null) {
                            throw new GraphValidationException(String.format(
                                "FORK node '%s' references joinNodeId '%s' which does not exist",
                                nodeId, joinNodeId));
                        }
                        if (joinNode.kind() != StepKind.JOIN && joinNode.kind() != StepKind.BARRIER) {
                            logger.warn("FORK node '{}' references joinNodeId '{}' but that node has kind={}, not JOIN",
                                nodeId, joinNodeId, joinNode.kind());
                        }
                    }
                }
            }

            if (node.kind() == StepKind.JOIN) {
                List<String> upstream = node.upstreamSteps();
                if (upstream == null || upstream.size() < 2) {
                    logger.debug("JOIN node '{}' has {} upstream steps (expected >= 2)",
                        nodeId, upstream != null ? upstream.size() : 0);
                }
            }
        }

        detectImplicitJoins(steps);
    }

    private void detectImplicitJoins(Map<String, StepNode> steps) {
        Map<String, Set<String>> upstreamMap = new HashMap<>();
        for (StepNode node : steps.values()) {
            List<String> nextSteps = node.nextSteps();
            if (nextSteps != null) {
                for (String next : nextSteps) {
                    upstreamMap.computeIfAbsent(next, k -> new HashSet<>()).add(node.nodeId());
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : upstreamMap.entrySet()) {
            String nodeId = entry.getKey();
            Set<String> upstream = entry.getValue();

            if (upstream.size() > 1) {
                StepNode node = steps.get(nodeId);
                if (node != null && node.kind() != StepKind.JOIN && node.kind() != StepKind.BARRIER) {
                    logger.warn("Node '{}' (kind={}) has {} incoming edges from {} but is not a JOIN. " +
                        "This may cause duplicate execution. Consider making it a JOIN node.",
                        nodeId, node.kind(), upstream.size(), upstream);
                }
            }
        }
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
        validateNodeExecutorsExist(nodes);
        validateNodeReferences(nodes, edges);
    }

    private void validateNodeReferences(List<NodeDefinition> nodes, List<Edge> edges) {
        Set<String> nodeIds = nodes.stream()
            .map(NodeDefinition::getId)
            .collect(Collectors.toSet());

        for (Edge edge : edges) {
            if (!nodeIds.contains(edge.getSource())) {
                throw new GraphValidationException(
                    "Edge references non-existent source node: " + edge.getSource());
            }
            if (!nodeIds.contains(edge.getTarget())) {
                throw new GraphValidationException(
                    "Edge references non-existent target node: " + edge.getTarget());
            }
        }
    }

    private void validateNodeExecutorsExist(List<NodeDefinition> nodes) {
        List<String> missingExecutors = new ArrayList<>();
        Map<String, String> executorMapping = new LinkedHashMap<>();

        for (NodeDefinition node : nodes) {
            String nodeType = node.getType();
            String normalizedType = normalize(nodeType);

            if (normalizedType.isEmpty()) {
                missingExecutors.add(nodeType);
                continue;
            }

            if (!nodeExecutorRegistry.hasExecutor(normalizedType)) {
                missingExecutors.add(normalizedType);
            } else {
                try {
                    String executorClass = nodeExecutorRegistry.getExecutor(normalizedType).getClass().getSimpleName();
                    executorMapping.put(normalizedType, executorClass);
                } catch (Exception e) {
                    missingExecutors.add(normalizedType);
                }
            }
        }

        if (!missingExecutors.isEmpty()) {
            throw new GraphValidationException(
                "No executor registered for node types: " + missingExecutors +
                ". Cannot execute workflow. Please ensure all node types have corresponding executors.");
        }

        logger.info("Executor mapping validated: {}", executorMapping);
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

    private List<OutputPort> determineOutputPorts(String nodeId, List<Edge> edges) {
        List<OutputPort> ports = new ArrayList<>();

        for (Edge edge : edges) {
            if (edge.getSource().equals(nodeId)) {
                String sourcePort = edge.getSourceHandle() != null ? edge.getSourceHandle() : "out";
                String targetPort = edge.getTargetHandle() != null ? edge.getTargetHandle() : "in";

                OutputPort port = new OutputPort(
                    edge.getTarget(),
                    sourcePort,
                    targetPort,
                    edge.isControl()
                );

                ports.add(port);
            }
        }

        return ports;
    }
}
