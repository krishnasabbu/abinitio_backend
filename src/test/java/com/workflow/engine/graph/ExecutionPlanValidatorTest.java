package com.workflow.engine.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.engine.model.ExecutionHints;
import com.workflow.engine.model.ExecutionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExecutionPlanValidator Tests")
public class ExecutionPlanValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Valid Plans")
    class ValidPlanTests {

        @Test
        @DisplayName("Simple linear plan passes validation")
        void simpleLinearPlanPasses() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", List.of("b"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("c"), null, StepKind.NORMAL));
            steps.put("c", createNode("c", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            assertDoesNotThrow(() -> ExecutionPlanValidator.validate(plan));
        }

        @Test
        @DisplayName("Fork-join plan with explicit JOIN passes")
        void forkJoinWithExplicitJoinPasses() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints parallelHints = new ExecutionHints();
            parallelHints.setMode(ExecutionMode.PARALLEL);

            steps.put("fork", createNodeWithHints("fork", List.of("a", "b"), null,
                StepKind.FORK, parallelHints));
            steps.put("a", createNode("a", List.of("join"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("join"), null, StepKind.NORMAL));
            steps.put("join", createNode("join", List.of("end"), null, StepKind.JOIN));
            steps.put("end", createNode("end", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("fork"), steps);

            assertDoesNotThrow(() -> ExecutionPlanValidator.validate(plan));
        }

        @Test
        @DisplayName("Plan with error handling passes")
        void planWithErrorHandlingPasses() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("start", createNode("start", List.of("risky"), null, StepKind.NORMAL));
            steps.put("risky", createNode("risky", List.of("success"), List.of("error"),
                StepKind.NORMAL));
            steps.put("success", createNode("success", null, null, StepKind.NORMAL));
            steps.put("error", createNode("error", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("start"), steps);

            assertDoesNotThrow(() -> ExecutionPlanValidator.validate(plan));
        }

        @Test
        @DisplayName("Multiple entry points pass validation")
        void multipleEntryPointsPass() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("entry1", createNode("entry1", List.of("merge"), null, StepKind.NORMAL));
            steps.put("entry2", createNode("entry2", List.of("merge"), null, StepKind.NORMAL));
            steps.put("merge", createNode("merge", null, null, StepKind.JOIN));

            ExecutionPlan plan = new ExecutionPlan(List.of("entry1", "entry2"), steps);

            assertDoesNotThrow(() -> ExecutionPlanValidator.validate(plan));
        }
    }

    @Nested
    @DisplayName("Cycle Detection")
    class CycleDetectionTests {

        @Test
        @DisplayName("Direct cycle A->B->A detected")
        void directCycleDetected() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", List.of("b"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("a"), null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            var exception = assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );

            assertTrue(exception.getMessage().contains("Cycle detected"),
                "Error should mention cycle: " + exception.getMessage());
        }

        @Test
        @DisplayName("Indirect cycle A->B->C->A detected")
        void indirectCycleDetected() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", List.of("b"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("c"), null, StepKind.NORMAL));
            steps.put("c", createNode("c", List.of("a"), null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            var exception = assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );

            assertTrue(exception.getMessage().contains("Cycle detected"));
        }

        @Test
        @DisplayName("Self-loop A->A detected")
        void selfLoopDetected() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", List.of("a"), null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );
        }
    }

    @Nested
    @DisplayName("Missing Reference Detection")
    class MissingReferenceTests {

        @Test
        @DisplayName("Missing nextStep reference detected")
        void missingNextStepDetected() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", List.of("non-existent"), null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            var exception = assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );

            assertTrue(exception.getMessage().contains("non-existent"));
        }

        @Test
        @DisplayName("Missing errorStep reference detected")
        void missingErrorStepDetected() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", null, List.of("missing-error"), StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            var exception = assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );

            assertTrue(exception.getMessage().contains("missing-error"));
        }

        @Test
        @DisplayName("Missing entry point reference detected")
        void missingEntryPointDetected() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("missing-entry"), steps);

            var exception = assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );

            assertTrue(exception.getMessage().contains("missing-entry"));
        }
    }

    @Nested
    @DisplayName("Implicit Join Detection")
    class ImplicitJoinTests {

        @Test
        @DisplayName("Implicit join without explicit JOIN kind fails")
        void implicitJoinWithoutExplicitKindFails() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints parallelHints = new ExecutionHints();
            parallelHints.setMode(ExecutionMode.PARALLEL);

            steps.put("fork", createNodeWithHints("fork", List.of("a", "b"), null,
                StepKind.FORK, parallelHints));
            steps.put("a", createNode("a", List.of("converge"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("converge"), null, StepKind.NORMAL));
            steps.put("converge", createNode("converge", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("fork"), steps);

            var exception = assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );

            assertTrue(exception.getMessage().toLowerCase().contains("implicit join") ||
                       exception.getMessage().toLowerCase().contains("converge"),
                "Should detect implicit join at converge: " + exception.getMessage());
        }

        @Test
        @DisplayName("Explicit JOIN at convergence point passes")
        void explicitJoinAtConvergencePasses() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints parallelHints = new ExecutionHints();
            parallelHints.setMode(ExecutionMode.PARALLEL);

            steps.put("fork", createNodeWithHints("fork", List.of("a", "b"), null,
                StepKind.FORK, parallelHints));
            steps.put("a", createNode("a", List.of("converge"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("converge"), null, StepKind.NORMAL));
            steps.put("converge", createNode("converge", null, null, StepKind.JOIN));

            ExecutionPlan plan = new ExecutionPlan(List.of("fork"), steps);

            assertDoesNotThrow(() -> ExecutionPlanValidator.validate(plan));
        }
    }

    @Nested
    @DisplayName("Empty Plan Detection")
    class EmptyPlanTests {

        @Test
        @DisplayName("Empty steps map fails")
        void emptyStepsMapFails() {
            ExecutionPlan plan = new ExecutionPlan(List.of("a"), Map.of());

            assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );
        }

        @Test
        @DisplayName("Empty entry points fails")
        void emptyEntryPointsFails() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of(), steps);

            assertThrows(
                ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan)
            );
        }
    }

    @Nested
    @DisplayName("ValidationResult API")
    class ValidationResultTests {

        @Test
        @DisplayName("validateWithResult returns valid result for good plan")
        void validateWithResultReturnsValidForGoodPlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", null, null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            var result = ExecutionPlanValidator.validateWithResult(plan);

            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("validateWithResult returns errors for bad plan")
        void validateWithResultReturnsErrorsForBadPlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("a", createNode("a", List.of("b"), null, StepKind.NORMAL));
            steps.put("b", createNode("b", List.of("a"), null, StepKind.NORMAL));

            ExecutionPlan plan = new ExecutionPlan(List.of("a"), steps);

            var result = ExecutionPlanValidator.validateWithResult(plan);

            assertFalse(result.valid());
            assertFalse(result.errors().isEmpty());
        }
    }

    private StepNode createNode(String nodeId, List<String> nextSteps,
                                List<String> errorSteps, StepKind kind) {
        return createNodeWithHints(nodeId, nextSteps, errorSteps, kind, null);
    }

    private StepNode createNodeWithHints(String nodeId, List<String> nextSteps,
                                         List<String> errorSteps, StepKind kind,
                                         ExecutionHints hints) {
        ObjectNode config = objectMapper.createObjectNode();
        return new StepNode(
            nodeId,
            "Filter",
            config,
            nextSteps,
            errorSteps,
            null,
            null,
            hints,
            StepClassification.TRANSFORM,
            null,
            kind,
            null
        );
    }
}
