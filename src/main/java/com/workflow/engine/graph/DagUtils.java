package com.workflow.engine.graph;

import com.workflow.engine.model.Edge;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for Directed Acyclic Graph (DAG) operations on workflow definitions.
 *
 * Provides algorithms for topological sorting, cycle detection, edge filtering, and
 * graph analysis. Used during workflow validation and execution planning to ensure
 * workflows are properly structured and can be executed in valid order.
 *
 * All methods are static and stateless, operating on WorkflowDefinition instances.
 * Thread safety: Thread-safe. All methods are stateless and use only local variables.
 *
 * @author Workflow Engine
 * @version 1.0
 */
public class DagUtils {

    private static final Logger logger = LoggerFactory.getLogger(DagUtils.class);

    /**
     * Performs a topological sort of workflow nodes using Kahn's algorithm.
     *
     * Orders nodes such that for every directed edge from node A to node B,
     * A appears before B in the ordering. This order represents the valid execution
     * sequence for the workflow nodes.
     *
     * @param workflow the workflow definition containing nodes and edges
     * @return ordered list of node IDs in topological sequence
     * @throws IllegalStateException if the workflow contains a cycle (not a valid DAG)
     */
    public static List<String> topologicalSort(WorkflowDefinition workflow) {
        logger.debug("Performing topological sort on workflow with {} nodes", workflow.getNodes().size());


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
            logger.error("Topological sort resulted in {} nodes, expected {}. Graph contains a cycle.",
                sorted.size(), workflow.getNodes().size());
            throw new IllegalStateException("Graph contains a cycle");
        }

        logger.debug("Topological sort completed successfully");
        return sorted;
    }

    /**
     * Detects if the workflow contains a cycle, making it invalid as a DAG.
     *
     * @param workflow the workflow definition to check
     * @return true if the workflow contains a cycle, false otherwise
     */
    public static boolean hasCycle(WorkflowDefinition workflow) {
        logger.debug("Checking for cycles in workflow");
        try {
            topologicalSort(workflow);
            logger.debug("No cycles detected");
            return false;
        } catch (IllegalStateException e) {
            logger.warn("Cycle detected in workflow");
            return true;
        }
    }

    /**
     * Builds an adjacency list representation of the workflow graph.
     *
     * Creates a map where each node ID maps to a list of its outgoing edge targets.
     * All nodes are initialized in the map, even if they have no outgoing edges.
     *
     * @param workflow the workflow definition
     * @return map of node IDs to their target node IDs
     */
    public static Map<String, List<String>> buildAdjacencyList(WorkflowDefinition workflow) {
        logger.debug("Building adjacency list for workflow");
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

    /**
     * Calculates the in-degree (number of incoming edges) for each node.
     *
     * The in-degree is the count of edges pointing to a node. Nodes with in-degree 0
     * are starting nodes that can be executed immediately.
     *
     * @param workflow the workflow definition
     * @return map of node IDs to their in-degree values
     */
    public static Map<String, Integer> calculateInDegree(WorkflowDefinition workflow) {
        logger.debug("Calculating in-degree for all nodes in workflow");
        Map<String, Integer> inDegree = new HashMap<>();

        for (NodeDefinition node : workflow.getNodes()) {
            inDegree.put(node.getId(), 0);
        }

        for (Edge edge : workflow.getEdges()) {
            inDegree.put(edge.getTarget(), inDegree.getOrDefault(edge.getTarget(), 0) + 1);
        }

        return inDegree;
    }

    /**
     * Determines if an edge is a control edge.
     *
     * Checks both the isControl flag and the edge type for robustness.
     * This handles cases where deserialization may not properly populate the isControl flag.
     *
     * @param edge the edge to check
     * @return true if the edge is a control edge, false if it's a data edge
     */
    private static boolean isControlEdge(Edge edge) {
        return edge.isControl() || "control".equalsIgnoreCase(edge.getType());
    }

    /**
     * Finds all source nodes that are not the designated start node.
     *
     * Source nodes are nodes with no incoming data edges (control edges from Start are ignored).
     * This method excludes the explicit "Start" node type from results and only considers
     * data edges (non-control edges) when determining if a node has incoming dependencies.
     *
     * @param workflow the workflow definition
     * @return list of source node IDs
     */
    public static List<String> findSourceNodes(WorkflowDefinition workflow) {
        logger.debug("Finding source nodes in workflow");

        List<Edge> edges = workflow.getEdges() != null ? workflow.getEdges() : new ArrayList<>();
        Set<String> dataTargets = edges.stream()
            .filter(e -> !isControlEdge(e))
            .map(Edge::getTarget)
            .collect(Collectors.toSet());

        return workflow.getNodes().stream()
            .map(NodeDefinition::getId)
            .filter(id -> !dataTargets.contains(id))
            .filter(id -> !isStartNode(workflow, id))
            .collect(Collectors.toList());
    }

    /**
     * Checks if a node is a "Start" node in the workflow.
     *
     * @param workflow the workflow definition
     * @param nodeId the node ID to check
     * @return true if the node is of type "Start", false otherwise
     */
    public static boolean isStartNode(WorkflowDefinition workflow, String nodeId) {
        logger.debug("Checking if node {} is a start node", nodeId);
        return workflow.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .anyMatch(n -> "Start".equalsIgnoreCase(n.getType()));
    }

    /**
     * Retrieves all edges that originate from the specified node.
     *
     * @param workflow the workflow definition
     * @param nodeId the source node ID
     * @return list of outgoing edges from the node
     */
    public static List<Edge> getOutgoingEdges(WorkflowDefinition workflow, String nodeId) {
        logger.debug("Getting outgoing edges from node: {}", nodeId);
        return workflow.getEdges().stream()
            .filter(e -> e.getSource().equals(nodeId))
            .collect(Collectors.toList());
    }

    /**
     * Retrieves all edges that target the specified node.
     *
     * @param workflow the workflow definition
     * @param nodeId the target node ID
     * @return list of incoming edges to the node
     */
    public static List<Edge> getIncomingEdges(WorkflowDefinition workflow, String nodeId) {
        logger.debug("Getting incoming edges to node: {}", nodeId);
        return workflow.getEdges().stream()
            .filter(e -> e.getTarget().equals(nodeId))
            .collect(Collectors.toList());
    }

    /**
     * Retrieves all control flow edges in the workflow.
     *
     * Control edges represent the logical flow and ordering of node execution,
     * as opposed to data edges which represent data passing between nodes.
     *
     * @param workflow the workflow definition
     * @return list of control edges
     */
    public static List<Edge> getControlEdges(WorkflowDefinition workflow) {
        logger.debug("Retrieving control edges from workflow");
        return workflow.getEdges().stream()
            .filter(Edge::isControl)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves all data flow edges in the workflow.
     *
     * Data edges represent the passing of data/items between nodes,
     * as opposed to control edges which represent execution ordering.
     *
     * @param workflow the workflow definition
     * @return list of data edges
     */
    public static List<Edge> getDataEdges(WorkflowDefinition workflow) {
        logger.debug("Retrieving data edges from workflow");
        return workflow.getEdges().stream()
            .filter(e -> !e.isControl())
            .collect(Collectors.toList());
    }

    /**
     * Identifies all parallel branches in the workflow.
     *
     * A parallel branch occurs when a node has multiple outgoing edges,
     * indicating that multiple nodes can execute concurrently.
     *
     * @param workflow the workflow definition
     * @return set of parallel branch paths (each path is a list of node IDs)
     */
    public static Set<List<String>> findParallelBranches(WorkflowDefinition workflow) {
        logger.debug("Finding parallel branches in workflow");
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

    /**
     * Retrieves a node definition by its ID.
     *
     * @param workflow the workflow definition
     * @param nodeId the node ID to look up
     * @return the node definition with the given ID
     * @throws IllegalArgumentException if no node with the given ID exists
     */
    public static NodeDefinition getNodeById(WorkflowDefinition workflow, String nodeId) {
        logger.debug("Looking up node by ID: {}", nodeId);
        return workflow.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElseThrow(() -> {
                logger.error("Node not found with ID: {}", nodeId);
                return new IllegalArgumentException("Node not found: " + nodeId);
            });
    }
}
