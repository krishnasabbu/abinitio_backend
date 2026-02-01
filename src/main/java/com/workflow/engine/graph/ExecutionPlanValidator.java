package com.workflow.engine.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates ExecutionPlan for structural correctness before job building.
 *
 * Performs comprehensive validation including:
 * - Cycle detection (prevents infinite loops)
 * - Missing step reference detection
 * - Implicit join detection (shared downstream without explicit JOIN)
 * - Orphan node detection (unreachable steps)
 * - Entry point validation
 *
 * All validation errors are collected and reported together for better UX.
 * Validation is fail-fast: throws ExecutionPlanValidationException with all errors.
 */
public class ExecutionPlanValidator {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionPlanValidator.class);

    private final ExecutionPlan plan;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ExecutionPlanValidator(ExecutionPlan plan) {
        this.plan = plan;
    }

    public static void validate(ExecutionPlan plan) {
        new ExecutionPlanValidator(plan).performValidation();
    }

    public static ValidationResult validateWithResult(ExecutionPlan plan) {
        ExecutionPlanValidator validator = new ExecutionPlanValidator(plan);
        try {
            validator.performValidation();
            return new ValidationResult(true, List.of(), validator.warnings);
        } catch (ExecutionPlanValidationException e) {
            return new ValidationResult(false, validator.errors, validator.warnings);
        }
    }

    private void performValidation() {
        logger.debug("Validating execution plan with {} steps", plan.steps().size());

        validateNotEmpty();
        validateEntryPoints();
        validateStepReferences();
        detectCycles();
        detectImplicitJoins();
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

    private void detectImplicitJoins() {
        Map<String, List<String>> incomingEdges = computeIncomingEdges();

        for (Map.Entry<String, List<String>> entry : incomingEdges.entrySet()) {
            String stepId = entry.getKey();
            List<String> incomers = entry.getValue();

            if (incomers.size() > 1) {
                StepNode node = plan.steps().get(stepId);
                if (node != null && !node.isJoin()) {
                    boolean hasParallelUpstream = hasParallelForkUpstream(stepId, incomers);
                    if (hasParallelUpstream) {
                        errors.add(String.format(
                            "Implicit join detected at step '%s'. Multiple branches (%s) converge " +
                            "without explicit JOIN node. Add kind=JOIN to this step or restructure " +
                            "the graph with an explicit join barrier.",
                            stepId, String.join(", ", incomers)));
                    } else {
                        warnings.add(String.format(
                            "Step '%s' has multiple incoming edges from %s. If these branches " +
                            "execute in parallel, consider making this an explicit JOIN node.",
                            stepId, incomers));
                    }
                }
            }
        }
    }

    private boolean hasParallelForkUpstream(String stepId, List<String> incomers) {
        Set<String> forkAncestors = new HashSet<>();
        for (String incomer : incomers) {
            findForkAncestors(incomer, forkAncestors, new HashSet<>());
        }
        return !forkAncestors.isEmpty();
    }

    private void findForkAncestors(String stepId, Set<String> forkAncestors, Set<String> visited) {
        if (visited.contains(stepId)) return;
        visited.add(stepId);

        StepNode node = plan.steps().get(stepId);
        if (node != null && node.isFork()) {
            forkAncestors.add(stepId);
        }

        Map<String, List<String>> incoming = computeIncomingEdges();
        List<String> predecessors = incoming.getOrDefault(stepId, List.of());
        for (String pred : predecessors) {
            findForkAncestors(pred, forkAncestors, visited);
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
        Map<String, List<String>> incomingEdges = computeIncomingEdges();

        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            String stepId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.isJoin()) {
                List<String> incomers = incomingEdges.get(stepId);
                if (incomers == null || incomers.size() < 2) {
                    warnings.add(String.format(
                        "JOIN node '%s' has only %d incoming edge(s). " +
                        "JOIN nodes typically synchronize multiple branches.",
                        stepId, incomers == null ? 0 : incomers.size()));
                }

                if (node.upstreamSteps() != null && !node.upstreamSteps().isEmpty()) {
                    Set<String> declared = new HashSet<>(node.upstreamSteps());
                    Set<String> actual = new HashSet<>(incomers);
                    if (!declared.equals(actual)) {
                        warnings.add(String.format(
                            "JOIN node '%s' upstreamSteps %s doesn't match actual " +
                            "incoming edges %s. Consider synchronizing these.",
                            stepId, declared, actual));
                    }
                }
            }
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
