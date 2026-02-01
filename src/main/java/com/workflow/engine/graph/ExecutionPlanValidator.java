package com.workflow.engine.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ExecutionPlanValidator {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionPlanValidator.class);

    private final ExecutionPlan plan;
    private final ValidationConfig config;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private Map<String, List<String>> incomingEdgesCache;

    public ExecutionPlanValidator(ExecutionPlan plan) {
        this(plan, ValidationConfig.defaults());
    }

    public ExecutionPlanValidator(ExecutionPlan plan, ValidationConfig config) {
        this.plan = plan;
        this.config = config;
    }

    public static void validate(ExecutionPlan plan) {
        new ExecutionPlanValidator(plan).performValidation();
    }

    public static void validate(ExecutionPlan plan, ValidationConfig config) {
        new ExecutionPlanValidator(plan, config).performValidation();
    }

    public static ValidationResult validateWithResult(ExecutionPlan plan) {
        return validateWithResult(plan, ValidationConfig.defaults());
    }

    public static ValidationResult validateWithResult(ExecutionPlan plan, ValidationConfig config) {
        ExecutionPlanValidator validator = new ExecutionPlanValidator(plan, config);
        try {
            validator.performValidation();
            return new ValidationResult(true, List.of(), validator.warnings);
        } catch (ExecutionPlanValidationException e) {
            return new ValidationResult(false, validator.errors, validator.warnings);
        }
    }

    private void performValidation() {
        logger.debug("Validating execution plan with {} steps (strictJoins={}, strictJoinUpstreams={}, requireExplicitJoin={})",
            plan.steps().size(), config.strictJoins(), config.strictJoinUpstreams(), config.requireExplicitJoin());

        this.incomingEdgesCache = computeIncomingEdges();

        validateNotEmpty();
        validateEntryPoints();
        validateStepReferences();
        detectCycles();
        validateConvergenceSemantics();
        validateForkJoinDeclarations();
        detectOrphanNodes();
        validateJoinNodes();

        if (!errors.isEmpty()) {
            String errorMessage = String.format(
                "Execution plan validation failed with %d error(s):\n%s",
                errors.size(),
                errors.stream()
                    .map(e -> "  - " + e)
                    .collect(Collectors.joining("\n"))
            );
            logger.error(errorMessage);
            throw new ExecutionPlanValidationException(errorMessage, errors);
        }

        if (!warnings.isEmpty()) {
            logger.warn("Execution plan has {} warning(s):\n{}",
                warnings.size(),
                warnings.stream()
                    .map(w -> "  - " + w)
                    .collect(Collectors.joining("\n"))
            );
        }

        logger.info("Execution plan validation passed");
    }

    private void validateNotEmpty() {
        if (plan.steps() == null || plan.steps().isEmpty()) {
            errors.add("Execution plan has no steps");
        }
    }

    private void validateEntryPoints() {
        if (plan.entryStepIds() == null || plan.entryStepIds().isEmpty()) {
            errors.add("Execution plan has no entry points");
            return;
        }

        for (String entryId : plan.entryStepIds()) {
            if (!plan.steps().containsKey(entryId)) {
                errors.add(String.format(
                    "Entry point '%s' references non-existent step", entryId));
            }
        }
    }

    private void validateStepReferences() {
        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            String stepId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.nextSteps() != null) {
                for (String nextId : node.nextSteps()) {
                    if (!plan.steps().containsKey(nextId)) {
                        errors.add(String.format(
                            "Step '%s' references non-existent nextStep '%s'",
                            stepId, nextId));
                    }
                }
            }

            if (node.errorSteps() != null) {
                for (String errorId : node.errorSteps()) {
                    if (!plan.steps().containsKey(errorId)) {
                        errors.add(String.format(
                            "Step '%s' references non-existent errorStep '%s'",
                            stepId, errorId));
                    }
                }
            }

            if (node.upstreamSteps() != null) {
                for (String upstreamId : node.upstreamSteps()) {
                    if (!plan.steps().containsKey(upstreamId)) {
                        errors.add(String.format(
                            "Step '%s' references non-existent upstreamStep '%s'",
                            stepId, upstreamId));
                    }
                }
            }
        }
    }

    private void detectCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String stepId : plan.steps().keySet()) {
            if (detectCyclesDfs(stepId, visited, recursionStack, new ArrayList<>())) {
                return;
            }
        }
    }

    private boolean detectCyclesDfs(String stepId, Set<String> visited,
                                    Set<String> recursionStack, List<String> path) {
        if (recursionStack.contains(stepId)) {
            int cycleStart = path.indexOf(stepId);
            List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
            cycle.add(stepId);
            errors.add(String.format("Cycle detected: %s", String.join(" -> ", cycle)));
            return true;
        }

        if (visited.contains(stepId)) {
            return false;
        }

        visited.add(stepId);
        recursionStack.add(stepId);
        path.add(stepId);

        StepNode node = plan.steps().get(stepId);
        if (node != null && node.nextSteps() != null) {
            for (String nextId : node.nextSteps()) {
                if (plan.steps().containsKey(nextId)) {
                    if (detectCyclesDfs(nextId, visited, recursionStack, path)) {
                        return true;
                    }
                }
            }
        }

        path.remove(path.size() - 1);
        recursionStack.remove(stepId);
        return false;
    }

    private void validateConvergenceSemantics() {
        for (Map.Entry<String, List<String>> entry : incomingEdgesCache.entrySet()) {
            String stepId = entry.getKey();
            List<String> incomers = entry.getValue();

            if (incomers.size() > 1) {
                StepNode node = plan.steps().get(stepId);
                if (node == null) continue;

                boolean isJoinOrBarrier = node.kind() == StepKind.JOIN || node.kind() == StepKind.BARRIER;
                boolean isDecisionExclusiveMerge = node.kind() == StepKind.DECISION &&
                    isExclusiveMergeFromDecision(stepId, incomers);

                if (!isJoinOrBarrier && !isDecisionExclusiveMerge) {
                    String message = String.format(
                        "Node '%s' (kind=%s) has %d incoming edges from %s but is not a JOIN/BARRIER. " +
                        "Convergence without explicit synchronization will cause duplicate execution or race conditions.",
                        stepId, node.kind(), incomers.size(), incomers);

                    if (config.strictJoins()) {
                        errors.add(message);
                    } else {
                        warnings.add(message + " Enable workflow.validation.strictJoins=true to enforce.");
                    }
                }
            }
        }
    }

    private boolean isExclusiveMergeFromDecision(String stepId, List<String> incomers) {
        Set<String> decisionSources = new HashSet<>();
        for (String incomer : incomers) {
            String decisionSource = findDecisionSource(incomer, new HashSet<>());
            if (decisionSource != null) {
                decisionSources.add(decisionSource);
            }
        }
        return decisionSources.size() == 1;
    }

    private String findDecisionSource(String stepId, Set<String> visited) {
        if (visited.contains(stepId)) return null;
        visited.add(stepId);

        StepNode node = plan.steps().get(stepId);
        if (node == null) return null;

        if (node.kind() == StepKind.DECISION) {
            return stepId;
        }

        List<String> predecessors = incomingEdgesCache.getOrDefault(stepId, List.of());
        if (predecessors.size() == 1) {
            return findDecisionSource(predecessors.get(0), visited);
        }

        return null;
    }

    private void validateForkJoinDeclarations() {
        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            String stepId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.kind() == StepKind.FORK) {
                List<String> branches = node.nextSteps();
                if (branches != null && branches.size() > 1) {
                    String joinNodeId = node.executionHints() != null
                        ? node.executionHints().getJoinNodeId()
                        : null;

                    if (joinNodeId == null) {
                        String message = String.format(
                            "FORK node '%s' has %d branches but no explicit joinNodeId declared. " +
                            "This creates ambiguous convergence behavior.",
                            stepId, branches.size());

                        if (config.requireExplicitJoin()) {
                            errors.add(message + " Set executionHints.joinNodeId or use workflow.validation.requireExplicitJoin=false.");
                        } else {
                            warnings.add(message);
                        }
                    } else {
                        StepNode joinNode = plan.steps().get(joinNodeId);
                        if (joinNode == null) {
                            errors.add(String.format(
                                "FORK node '%s' references non-existent joinNodeId '%s'",
                                stepId, joinNodeId));
                        } else if (joinNode.kind() != StepKind.JOIN && joinNode.kind() != StepKind.BARRIER) {
                            errors.add(String.format(
                                "FORK node '%s' references joinNodeId '%s' but that node has kind=%s, not JOIN/BARRIER",
                                stepId, joinNodeId, joinNode.kind()));
                        } else {
                            validateBranchesReachJoin(stepId, branches, joinNodeId);
                        }
                    }
                }
            }

            if (node.kind() == StepKind.DECISION) {
                logger.debug("DECISION node '{}' present - ensure JobExecutionDecider is implemented", stepId);
            }

            if (node.kind() == StepKind.SUBGRAPH) {
                logger.debug("SUBGRAPH node '{}' present - ensure expansion is implemented", stepId);
            }
        }
    }

    private void validateBranchesReachJoin(String forkId, List<String> branches, String joinNodeId) {
        for (String branch : branches) {
            if (!canReach(branch, joinNodeId, new HashSet<>())) {
                errors.add(String.format(
                    "FORK '%s' branch '%s' cannot reach declared join '%s'. " +
                    "All branches must converge at the join point.",
                    forkId, branch, joinNodeId));
            }
        }
    }

    private boolean canReach(String from, String target, Set<String> visited) {
        if (from.equals(target)) return true;
        if (visited.contains(from)) return false;
        visited.add(from);

        StepNode node = plan.steps().get(from);
        if (node == null) return false;

        if (node.nextSteps() != null) {
            for (String next : node.nextSteps()) {
                if (canReach(next, target, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void detectOrphanNodes() {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>(plan.entryStepIds());

        while (!queue.isEmpty()) {
            String stepId = queue.poll();
            if (reachable.contains(stepId)) continue;
            reachable.add(stepId);

            StepNode node = plan.steps().get(stepId);
            if (node != null) {
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
        }

        Set<String> orphans = new HashSet<>(plan.steps().keySet());
        orphans.removeAll(reachable);

        if (!orphans.isEmpty()) {
            warnings.add(String.format(
                "Orphan steps detected (unreachable from entry points): %s",
                orphans));
        }
    }

    private void validateJoinNodes() {
        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            String stepId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.isJoin()) {
                List<String> actualIncomers = incomingEdgesCache.get(stepId);
                if (actualIncomers == null || actualIncomers.size() < 2) {
                    warnings.add(String.format(
                        "JOIN node '%s' has only %d incoming edge(s). " +
                        "JOIN nodes typically synchronize multiple branches.",
                        stepId, actualIncomers == null ? 0 : actualIncomers.size()));
                }

                if (node.upstreamSteps() != null && !node.upstreamSteps().isEmpty() && actualIncomers != null) {
                    Set<String> declared = new HashSet<>(node.upstreamSteps());
                    Set<String> actual = new HashSet<>(actualIncomers);

                    if (!declared.equals(actual)) {
                        String message = String.format(
                            "JOIN node '%s' upstreamSteps %s doesn't match actual incoming edges %s. " +
                            "This mismatch may cause synchronization issues.",
                            stepId, declared, actual);

                        if (config.strictJoinUpstreams()) {
                            errors.add(message);
                        } else {
                            warnings.add(message + " Enable workflow.validation.strictJoinUpstreams=true to enforce.");
                        }
                    }
                }
            }
        }
    }

    private Map<String, List<String>> computeIncomingEdges() {
        Map<String, List<String>> incomingEdges = new HashMap<>();

        for (String stepId : plan.steps().keySet()) {
            incomingEdges.put(stepId, new ArrayList<>());
        }

        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            String sourceId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.nextSteps() != null) {
                for (String targetId : node.nextSteps()) {
                    if (incomingEdges.containsKey(targetId)) {
                        incomingEdges.get(targetId).add(sourceId);
                    }
                }
            }
        }

        return incomingEdges;
    }

    public record ValidationConfig(
        boolean strictJoins,
        boolean strictJoinUpstreams,
        boolean requireExplicitJoin
    ) {
        public static ValidationConfig defaults() {
            return new ValidationConfig(false, false, false);
        }

        public static ValidationConfig strict() {
            return new ValidationConfig(true, true, true);
        }

        public static ValidationConfig fromProperties(
            boolean strictJoins,
            boolean strictJoinUpstreams,
            boolean requireExplicitJoin
        ) {
            return new ValidationConfig(strictJoins, strictJoinUpstreams, requireExplicitJoin);
        }
    }

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {}

    public static class ExecutionPlanValidationException extends RuntimeException {
        private final List<String> validationErrors;

        public ExecutionPlanValidationException(String message, List<String> errors) {
            super(message);
            this.validationErrors = new ArrayList<>(errors);
        }

        public List<String> getValidationErrors() {
            return Collections.unmodifiableList(validationErrors);
        }
    }
}
