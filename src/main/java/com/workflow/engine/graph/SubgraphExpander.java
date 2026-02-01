package com.workflow.engine.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.engine.model.ExecutionHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SubgraphExpander {

    private static final Logger logger = LoggerFactory.getLogger(SubgraphExpander.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, SubgraphDefinition> subgraphRegistry;
    private final int maxExpansionDepth;

    public SubgraphExpander() {
        this(new HashMap<>(), 10);
    }

    public SubgraphExpander(Map<String, SubgraphDefinition> subgraphRegistry, int maxExpansionDepth) {
        this.subgraphRegistry = subgraphRegistry;
        this.maxExpansionDepth = maxExpansionDepth;
    }

    public void registerSubgraph(String subgraphId, SubgraphDefinition definition) {
        subgraphRegistry.put(subgraphId, definition);
        logger.info("[SUBGRAPH] Registered subgraph '{}' with {} steps", subgraphId, definition.steps().size());
    }

    public ExecutionPlan expand(ExecutionPlan plan) {
        return expand(plan, 0);
    }

    private ExecutionPlan expand(ExecutionPlan plan, int depth) {
        if (depth > maxExpansionDepth) {
            throw new GraphValidationException(
                "Maximum subgraph expansion depth exceeded (" + maxExpansionDepth + "). " +
                "Check for circular subgraph references.");
        }

        Map<String, StepNode> expandedSteps = new LinkedHashMap<>();
        List<String> expandedEntryPoints = new ArrayList<>();
        Map<String, String> remapping = new HashMap<>();

        for (String entryId : plan.entryStepIds()) {
            StepNode entryNode = plan.steps().get(entryId);
            if (entryNode != null && entryNode.kind() == StepKind.SUBGRAPH) {
                SubgraphExpansionResult result = expandSubgraphNode(entryNode, plan, depth);
                expandedSteps.putAll(result.expandedSteps());
                expandedEntryPoints.addAll(result.entryPointIds());
                remapping.putAll(result.exitRemapping());
            } else {
                expandedEntryPoints.add(entryId);
            }
        }

        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            String nodeId = entry.getKey();
            StepNode node = entry.getValue();

            if (expandedSteps.containsKey(nodeId)) {
                continue;
            }

            if (node.kind() == StepKind.SUBGRAPH) {
                SubgraphExpansionResult result = expandSubgraphNode(node, plan, depth);
                expandedSteps.putAll(result.expandedSteps());
                remapping.putAll(result.exitRemapping());
            } else {
                expandedSteps.put(nodeId, node);
            }
        }

        Map<String, StepNode> rewiredSteps = rewireReferences(expandedSteps, remapping);

        ExecutionPlan expandedPlan = new ExecutionPlan(expandedEntryPoints, rewiredSteps, plan.workflowId());

        boolean hasSubgraphs = rewiredSteps.values().stream()
            .anyMatch(n -> n.kind() == StepKind.SUBGRAPH);

        if (hasSubgraphs) {
            return expand(expandedPlan, depth + 1);
        }

        logger.info("[SUBGRAPH] Expansion complete. Original steps: {}, Expanded steps: {}",
            plan.steps().size(), rewiredSteps.size());

        return expandedPlan;
    }

    private SubgraphExpansionResult expandSubgraphNode(StepNode subgraphNode, ExecutionPlan parentPlan, int depth) {
        String subgraphId = extractSubgraphId(subgraphNode);
        String instancePrefix = subgraphNode.nodeId() + "_";

        logger.debug("[SUBGRAPH] Expanding '{}' (subgraphId='{}') at depth {}",
            subgraphNode.nodeId(), subgraphId, depth);

        SubgraphDefinition definition = subgraphRegistry.get(subgraphId);
        if (definition == null) {
            if (subgraphNode.config() != null && subgraphNode.config().has("inlineSteps")) {
                definition = parseInlineSubgraph(subgraphNode.config());
            }
        }

        if (definition == null) {
            throw new GraphValidationException(String.format(
                "SUBGRAPH node '%s' references unknown subgraph '%s'. " +
                "Register the subgraph or provide inlineSteps in config.",
                subgraphNode.nodeId(), subgraphId));
        }

        Map<String, StepNode> expandedSteps = new LinkedHashMap<>();
        Map<String, String> idMapping = new HashMap<>();

        for (Map.Entry<String, StepNode> entry : definition.steps().entrySet()) {
            String originalId = entry.getKey();
            String newId = instancePrefix + originalId;
            idMapping.put(originalId, newId);

            StepNode originalNode = entry.getValue();
            StepNode renamedNode = renameNodeReferences(originalNode, newId, idMapping, instancePrefix);
            expandedSteps.put(newId, renamedNode);
        }

        List<String> entryPointIds = definition.entryPoints().stream()
            .map(id -> instancePrefix + id)
            .collect(Collectors.toList());

        Map<String, String> exitRemapping = new HashMap<>();
        exitRemapping.put(subgraphNode.nodeId(), instancePrefix + definition.exitPoint());

        List<String> parentNextSteps = subgraphNode.nextSteps();
        if (parentNextSteps != null && !parentNextSteps.isEmpty()) {
            String exitNodeId = instancePrefix + definition.exitPoint();
            StepNode exitNode = expandedSteps.get(exitNodeId);
            if (exitNode != null) {
                List<String> newNextSteps = new ArrayList<>();
                if (exitNode.nextSteps() != null) {
                    newNextSteps.addAll(exitNode.nextSteps());
                }
                newNextSteps.addAll(parentNextSteps);

                StepNode updatedExitNode = new StepNode(
                    exitNode.nodeId(),
                    exitNode.nodeType(),
                    exitNode.config(),
                    newNextSteps,
                    exitNode.errorSteps(),
                    exitNode.metrics(),
                    exitNode.exceptionHandling(),
                    exitNode.executionHints(),
                    exitNode.classification(),
                    exitNode.outputPorts(),
                    exitNode.kind(),
                    exitNode.upstreamSteps()
                );
                expandedSteps.put(exitNodeId, updatedExitNode);
            }
        }

        return new SubgraphExpansionResult(expandedSteps, entryPointIds, exitRemapping);
    }

    private String extractSubgraphId(StepNode node) {
        if (node.config() != null && node.config().has("subgraphId")) {
            return node.config().get("subgraphId").asText();
        }
        if (node.config() != null && node.config().has("templateId")) {
            return node.config().get("templateId").asText();
        }
        return node.nodeId() + "_subgraph";
    }

    private SubgraphDefinition parseInlineSubgraph(JsonNode config) {
        JsonNode inlineSteps = config.get("inlineSteps");
        if (!inlineSteps.isObject()) {
            throw new GraphValidationException("inlineSteps must be an object mapping step IDs to step definitions");
        }

        Map<String, StepNode> steps = new LinkedHashMap<>();
        List<String> entryPoints = new ArrayList<>();
        String exitPoint = null;

        Iterator<Map.Entry<String, JsonNode>> fields = inlineSteps.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String stepId = entry.getKey();
            JsonNode stepConfig = entry.getValue();

            StepNode stepNode = parseStepNode(stepId, stepConfig);
            steps.put(stepId, stepNode);

            if (stepConfig.has("isEntry") && stepConfig.get("isEntry").asBoolean()) {
                entryPoints.add(stepId);
            }
            if (stepConfig.has("isExit") && stepConfig.get("isExit").asBoolean()) {
                exitPoint = stepId;
            }
        }

        if (entryPoints.isEmpty() && !steps.isEmpty()) {
            entryPoints.add(steps.keySet().iterator().next());
        }

        if (exitPoint == null && !steps.isEmpty()) {
            exitPoint = steps.keySet().stream()
                .reduce((first, second) -> second)
                .orElse(null);
        }

        return new SubgraphDefinition(steps, entryPoints, exitPoint);
    }

    private StepNode parseStepNode(String stepId, JsonNode stepConfig) {
        String nodeType = stepConfig.has("type") ? stepConfig.get("type").asText() : "Compute";

        List<String> nextSteps = new ArrayList<>();
        if (stepConfig.has("nextSteps") && stepConfig.get("nextSteps").isArray()) {
            for (JsonNode next : stepConfig.get("nextSteps")) {
                nextSteps.add(next.asText());
            }
        }

        List<String> errorSteps = new ArrayList<>();
        if (stepConfig.has("errorSteps") && stepConfig.get("errorSteps").isArray()) {
            for (JsonNode err : stepConfig.get("errorSteps")) {
                errorSteps.add(err.asText());
            }
        }

        StepKind kind = StepKind.NORMAL;
        if (stepConfig.has("kind")) {
            try {
                kind = StepKind.valueOf(stepConfig.get("kind").asText().toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown step kind '{}' for step '{}', defaulting to NORMAL",
                    stepConfig.get("kind").asText(), stepId);
            }
        }

        JsonNode config = stepConfig.has("config") ? stepConfig.get("config") : objectMapper.createObjectNode();

        return new StepNode(
            stepId,
            nodeType,
            config,
            nextSteps.isEmpty() ? null : nextSteps,
            errorSteps.isEmpty() ? null : errorSteps,
            null,
            null,
            null,
            null,
            null,
            kind,
            null
        );
    }

    private StepNode renameNodeReferences(StepNode original, String newId, Map<String, String> idMapping, String prefix) {
        List<String> newNextSteps = null;
        if (original.nextSteps() != null) {
            newNextSteps = original.nextSteps().stream()
                .map(id -> idMapping.getOrDefault(id, prefix + id))
                .collect(Collectors.toList());
        }

        List<String> newErrorSteps = null;
        if (original.errorSteps() != null) {
            newErrorSteps = original.errorSteps().stream()
                .map(id -> idMapping.getOrDefault(id, prefix + id))
                .collect(Collectors.toList());
        }

        List<String> newUpstreamSteps = null;
        if (original.upstreamSteps() != null) {
            newUpstreamSteps = original.upstreamSteps().stream()
                .map(id -> idMapping.getOrDefault(id, prefix + id))
                .collect(Collectors.toList());
        }

        ExecutionHints newHints = original.executionHints();
        if (newHints != null && newHints.getJoinNodeId() != null) {
            String mappedJoinId = idMapping.getOrDefault(
                newHints.getJoinNodeId(),
                prefix + newHints.getJoinNodeId()
            );
            newHints = new ExecutionHints();
            newHints.setJoinNodeId(mappedJoinId);
            newHints.setMode(original.executionHints().getMode());
            newHints.setChunkSize(original.executionHints().getChunkSize());
            newHints.setPartitionCount(original.executionHints().getPartitionCount());
        }

        return new StepNode(
            newId,
            original.nodeType(),
            original.config(),
            newNextSteps,
            newErrorSteps,
            original.metrics(),
            original.exceptionHandling(),
            newHints,
            original.classification(),
            original.outputPorts(),
            original.kind(),
            newUpstreamSteps
        );
    }

    private Map<String, StepNode> rewireReferences(Map<String, StepNode> steps, Map<String, String> remapping) {
        if (remapping.isEmpty()) {
            return steps;
        }

        Map<String, StepNode> rewired = new LinkedHashMap<>();

        for (Map.Entry<String, StepNode> entry : steps.entrySet()) {
            String nodeId = entry.getKey();
            StepNode node = entry.getValue();

            List<String> newNextSteps = null;
            if (node.nextSteps() != null) {
                newNextSteps = node.nextSteps().stream()
                    .map(id -> remapping.getOrDefault(id, id))
                    .collect(Collectors.toList());
            }

            List<String> newErrorSteps = null;
            if (node.errorSteps() != null) {
                newErrorSteps = node.errorSteps().stream()
                    .map(id -> remapping.getOrDefault(id, id))
                    .collect(Collectors.toList());
            }

            List<String> newUpstreamSteps = null;
            if (node.upstreamSteps() != null) {
                newUpstreamSteps = node.upstreamSteps().stream()
                    .map(id -> remapping.getOrDefault(id, id))
                    .collect(Collectors.toList());
            }

            if (!Objects.equals(node.nextSteps(), newNextSteps) ||
                !Objects.equals(node.errorSteps(), newErrorSteps) ||
                !Objects.equals(node.upstreamSteps(), newUpstreamSteps)) {

                node = new StepNode(
                    node.nodeId(),
                    node.nodeType(),
                    node.config(),
                    newNextSteps,
                    newErrorSteps,
                    node.metrics(),
                    node.exceptionHandling(),
                    node.executionHints(),
                    node.classification(),
                    node.outputPorts(),
                    node.kind(),
                    newUpstreamSteps
                );
            }

            rewired.put(nodeId, node);
        }

        return rewired;
    }

    public record SubgraphDefinition(
        Map<String, StepNode> steps,
        List<String> entryPoints,
        String exitPoint
    ) {}

    private record SubgraphExpansionResult(
        Map<String, StepNode> expandedSteps,
        List<String> entryPointIds,
        Map<String, String> exitRemapping
    ) {}
}
