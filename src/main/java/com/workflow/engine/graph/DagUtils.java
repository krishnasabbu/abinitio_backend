package com.workflow.engine.graph;

import com.workflow.engine.model.Edge;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.WorkflowDefinition;

import java.util.*;
import java.util.stream.Collectors;

public class DagUtils {

    public static List<String> topologicalSort(WorkflowDefinition workflow) {
        Map<String, List<String>> adjacencyList = buildAdjacencyList(workflow);
        Map<String, Integer> inDegree = calculateInDegree(workflow);

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);

            List<String> neighbors = adjacencyList.getOrDefault(node, Collections.emptyList());
            for (String neighbor : neighbors) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (sorted.size() != workflow.getNodes().size()) {
            throw new IllegalStateException("Graph contains a cycle");
        }

        return sorted;
    }

    public static boolean hasCycle(WorkflowDefinition workflow) {
        try {
            topologicalSort(workflow);
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static Map<String, List<String>> buildAdjacencyList(WorkflowDefinition workflow) {
        Map<String, List<String>> adjacencyList = new HashMap<>();

        for (NodeDefinition node : workflow.getNodes()) {
            adjacencyList.putIfAbsent(node.getId(), new ArrayList<>());
        }

        for (Edge edge : workflow.getEdges()) {
            adjacencyList.computeIfAbsent(edge.getSource(), k -> new ArrayList<>())
                        .add(edge.getTarget());
        }

        return adjacencyList;
    }

    public static Map<String, Integer> calculateInDegree(WorkflowDefinition workflow) {
        Map<String, Integer> inDegree = new HashMap<>();

        for (NodeDefinition node : workflow.getNodes()) {
            inDegree.put(node.getId(), 0);
        }

        for (Edge edge : workflow.getEdges()) {
            inDegree.put(edge.getTarget(), inDegree.getOrDefault(edge.getTarget(), 0) + 1);
        }

        return inDegree;
    }

    public static List<String> findSourceNodes(WorkflowDefinition workflow) {
        Set<String> targets = workflow.getEdges().stream()
            .map(Edge::getTarget)
            .collect(Collectors.toSet());

        return workflow.getNodes().stream()
            .map(NodeDefinition::getId)
            .filter(id -> !targets.contains(id))
            .filter(id -> !isStartNode(workflow, id))
            .collect(Collectors.toList());
    }

    public static boolean isStartNode(WorkflowDefinition workflow, String nodeId) {
        return workflow.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .anyMatch(n -> "Start".equalsIgnoreCase(n.getType()));
    }

    public static List<Edge> getOutgoingEdges(WorkflowDefinition workflow, String nodeId) {
        return workflow.getEdges().stream()
            .filter(e -> e.getSource().equals(nodeId))
            .collect(Collectors.toList());
    }

    public static List<Edge> getIncomingEdges(WorkflowDefinition workflow, String nodeId) {
        return workflow.getEdges().stream()
            .filter(e -> e.getTarget().equals(nodeId))
            .collect(Collectors.toList());
    }

    public static List<Edge> getControlEdges(WorkflowDefinition workflow) {
        return workflow.getEdges().stream()
            .filter(Edge::isControl)
            .collect(Collectors.toList());
    }

    public static List<Edge> getDataEdges(WorkflowDefinition workflow) {
        return workflow.getEdges().stream()
            .filter(e -> !e.isControl())
            .collect(Collectors.toList());
    }

    public static Set<List<String>> findParallelBranches(WorkflowDefinition workflow) {
        Map<String, List<String>> adjacencyList = buildAdjacencyList(workflow);
        Set<List<String>> parallelBranches = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> branches = new ArrayList<>(entry.getValue());
                parallelBranches.add(branches);
            }
        }

        return parallelBranches;
    }

    public static NodeDefinition getNodeById(WorkflowDefinition workflow, String nodeId) {
        return workflow.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));
    }
}
