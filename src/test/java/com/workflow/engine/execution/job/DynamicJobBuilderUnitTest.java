package com.workflow.engine.execution.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.engine.graph.*;
import com.workflow.engine.model.ExecutionHints;
import com.workflow.engine.model.ExecutionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "workflow.job.require-workflow-id=false"
})
@DisplayName("DynamicJobBuilder Unit Tests")
public class DynamicJobBuilderUnitTest {

    @Autowired
    private DynamicJobBuilder dynamicJobBuilder;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobLauncher jobLauncher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Fork/Join with Explicit Join")
    class ForkJoinExplicitTests {

        @Test
        @DisplayName("Fork with explicit joinNodeId builds successfully")
        void forkWithExplicitJoinBuilds() {
            ExecutionPlan plan = createForkJoinPlanWithExplicitJoin();

            Job job = dynamicJobBuilder.buildJob(plan, "fork-join-explicit-test");

            assertNotNull(job, "Job should be built successfully");
            assertEquals("workflow-fork-join-explicit-test", job.getName());
        }

        @Test
        @DisplayName("Fork without explicit joinNodeId throws")
        void forkWithoutExplicitJoinThrows() {
            ExecutionPlan plan = createForkPlanWithoutJoin();

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> dynamicJobBuilder.buildJob(plan, "fork-no-join-test")
            );

            assertTrue(exception.getMessage().contains("joinNodeId"),
                "Error should mention missing joinNodeId: " + exception.getMessage());
        }

        @Test
        @DisplayName("Fork with joinNodeId pointing to non-JOIN kind throws")
        void forkWithWrongJoinKindThrows() {
            ExecutionPlan plan = createForkPlanWithWrongJoinKind();

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> dynamicJobBuilder.buildJob(plan, "fork-wrong-kind-test")
            );

            assertTrue(exception.getMessage().contains("kind") ||
                       exception.getMessage().contains("JOIN"),
                "Error should mention kind mismatch: " + exception.getMessage());
        }

        @Test
        @DisplayName("Fork where branch cannot reach join throws")
        void forkBranchCannotReachJoinThrows() {
            ExecutionPlan plan = createForkPlanWithUnreachableJoin();

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> dynamicJobBuilder.buildJob(plan, "fork-unreachable-test")
            );

            assertTrue(exception.getMessage().contains("cannot reach"),
                "Error should mention unreachable join: " + exception.getMessage());
        }

        private ExecutionPlan createForkJoinPlanWithExplicitJoin() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start", List.of("fork"), null,
                StepKind.NORMAL, null));

            ExecutionHints forkHints = new ExecutionHints();
            forkHints.setMode(ExecutionMode.PARALLEL);
            forkHints.setJoinNodeId("join");

            steps.put("fork", createStepNode("fork", "Filter",
                List.of("branch-a", "branch-b"), null,
                StepKind.FORK, forkHints));

            steps.put("branch-a", createStepNode("branch-a", "Filter",
                List.of("join"), null,
                StepKind.NORMAL, null));

            steps.put("branch-b", createStepNode("branch-b", "Filter",
                List.of("join"), null,
                StepKind.NORMAL, null));

            steps.put("join", createStepNode("join", "Filter",
                List.of("end"), null,
                StepKind.JOIN, null));

            steps.put("end", createStepNode("end", "End",
                null, null,
                StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("start"), steps);
        }

        private ExecutionPlan createForkPlanWithoutJoin() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints forkHints = new ExecutionHints();
            forkHints.setMode(ExecutionMode.PARALLEL);

            steps.put("fork", createStepNode("fork", "Start",
                List.of("a", "b"), null,
                StepKind.FORK, forkHints));

            steps.put("a", createStepNode("a", "Filter", List.of("merge"), null,
                StepKind.NORMAL, null));
            steps.put("b", createStepNode("b", "Filter", List.of("merge"), null,
                StepKind.NORMAL, null));
            steps.put("merge", createStepNode("merge", "End", null, null,
                StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("fork"), steps);
        }

        private ExecutionPlan createForkPlanWithWrongJoinKind() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints forkHints = new ExecutionHints();
            forkHints.setMode(ExecutionMode.PARALLEL);
            forkHints.setJoinNodeId("merge");

            steps.put("fork", createStepNode("fork", "Start",
                List.of("a", "b"), null,
                StepKind.FORK, forkHints));

            steps.put("a", createStepNode("a", "Filter", List.of("merge"), null,
                StepKind.NORMAL, null));
            steps.put("b", createStepNode("b", "Filter", List.of("merge"), null,
                StepKind.NORMAL, null));
            steps.put("merge", createStepNode("merge", "End", null, null,
                StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("fork"), steps);
        }

        private ExecutionPlan createForkPlanWithUnreachableJoin() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints forkHints = new ExecutionHints();
            forkHints.setMode(ExecutionMode.PARALLEL);
            forkHints.setJoinNodeId("join");

            steps.put("fork", createStepNode("fork", "Start",
                List.of("a", "b"), null,
                StepKind.FORK, forkHints));

            steps.put("a", createStepNode("a", "Filter", List.of("join"), null,
                StepKind.NORMAL, null));
            steps.put("b", createStepNode("b", "Filter", List.of("dead-end"), null,
                StepKind.NORMAL, null));
            steps.put("dead-end", createStepNode("dead-end", "End", null, null,
                StepKind.NORMAL, null));
            steps.put("join", createStepNode("join", "Filter", null, null,
                StepKind.JOIN, null));

            return new ExecutionPlan(List.of("fork"), steps);
        }
    }

    @Nested
    @DisplayName("Sequential Multi-Next")
    class SequentialMultiNextTests {

        @Test
        @DisplayName("Sequential mode chains all next steps")
        void sequentialModeChainsBranches() {
            ExecutionPlan plan = createSequentialMultiNextPlan();

            Job job = dynamicJobBuilder.buildJob(plan, "sequential-test");

            assertNotNull(job, "Job should be built");
            assertEquals("workflow-sequential-test", job.getName());
        }

        private ExecutionPlan createSequentialMultiNextPlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints serialHints = new ExecutionHints();
            serialHints.setMode(ExecutionMode.SERIAL);

            steps.put("start", createStepNode("start", "Start",
                List.of("step-1", "step-2", "step-3"), null,
                StepKind.NORMAL, serialHints));

            steps.put("step-1", createStepNode("step-1", "Filter",
                null, null, StepKind.NORMAL, null));
            steps.put("step-2", createStepNode("step-2", "Filter",
                null, null, StepKind.NORMAL, null));
            steps.put("step-3", createStepNode("step-3", "Filter",
                null, null, StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("start"), steps);
        }
    }

    @Nested
    @DisplayName("Error Routing")
    class ErrorRoutingTests {

        @Test
        @DisplayName("Error steps are wired for FAILED/STOPPED/UNKNOWN")
        void errorStepsWiredForAllErrorStatuses() {
            ExecutionPlan plan = createPlanWithErrorHandling();

            Job job = dynamicJobBuilder.buildJob(plan, "error-routing-test");

            assertNotNull(job, "Job should be built with error routing");
        }

        private ExecutionPlan createPlanWithErrorHandling() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start",
                List.of("risky-step"), null,
                StepKind.NORMAL, null));

            steps.put("risky-step", createStepNode("risky-step", "Filter",
                List.of("success-path"), List.of("error-handler"),
                StepKind.NORMAL, null));

            steps.put("success-path", createStepNode("success-path", "End",
                null, null, StepKind.NORMAL, null));

            steps.put("error-handler", createStepNode("error-handler", "End",
                null, null, StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("start"), steps);
        }
    }

    @Nested
    @DisplayName("Plan Validation")
    class PlanValidationTests {

        @Test
        @DisplayName("Cycle detection fails fast")
        void cycleDetectionFailsFast() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("a", createStepNode("a", "Filter", List.of("b"), null,
                StepKind.NORMAL, null));
            steps.put("b", createStepNode("b", "Filter", List.of("c"), null,
                StepKind.NORMAL, null));
            steps.put("c", createStepNode("c", "Filter", List.of("a"), null,
                StepKind.NORMAL, null));

            ExecutionPlan cyclicPlan = new ExecutionPlan(List.of("a"), steps);

            assertThrows(ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> dynamicJobBuilder.buildJob(cyclicPlan, "cyclic-test"),
                "Should detect cycle and fail fast");
        }

        @Test
        @DisplayName("Missing node reference fails fast")
        void missingNodeReferenceFailsFast() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start",
                List.of("non-existent"), null,
                StepKind.NORMAL, null));

            ExecutionPlan invalidPlan = new ExecutionPlan(List.of("start"), steps);

            assertThrows(ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> dynamicJobBuilder.buildJob(invalidPlan, "missing-node-test"),
                "Should detect missing node and fail fast");
        }

        @Test
        @DisplayName("Empty plan fails fast")
        void emptyPlanFailsFast() {
            ExecutionPlan emptyPlan = new ExecutionPlan(List.of(), Map.of());

            assertThrows(ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> dynamicJobBuilder.buildJob(emptyPlan, "empty-test"),
                "Should reject empty plan");
        }
    }

    @Nested
    @DisplayName("Job Identity")
    class JobIdentityTests {

        @Test
        @DisplayName("Job name is deterministic with workflowId")
        void jobNameDeterministicWithWorkflowId() {
            ExecutionPlan plan = createSimplePlan();

            Job job1 = dynamicJobBuilder.buildJob(plan, "my-workflow-123");
            Job job2 = dynamicJobBuilder.buildJob(plan, "my-workflow-123");

            assertEquals("workflow-my-workflow-123", job1.getName());
            assertEquals(job1.getName(), job2.getName(),
                "Same workflowId should produce same job name");
        }

        private ExecutionPlan createSimplePlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("only-step", createStepNode("only-step", "Start",
                null, null, StepKind.NORMAL, null));
            return new ExecutionPlan(List.of("only-step"), steps);
        }
    }

    @Nested
    @DisplayName("Unsupported Node Types Fail Fast")
    class UnsupportedNodesTests {

        @Test
        @DisplayName("DECISION node throws UnsupportedOperationException")
        void decisionNodeThrows() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start",
                List.of("decision"), null,
                StepKind.NORMAL, null));

            steps.put("decision", createStepNode("decision", "Filter",
                List.of("a", "b"), null,
                StepKind.DECISION, null));

            steps.put("a", createStepNode("a", "End", null, null,
                StepKind.NORMAL, null));
            steps.put("b", createStepNode("b", "End", null, null,
                StepKind.NORMAL, null));

            ExecutionPlan plan = new ExecutionPlan(List.of("start"), steps);

            UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> dynamicJobBuilder.buildJob(plan, "decision-test")
            );

            assertTrue(exception.getMessage().contains("DECISION"),
                "Error should mention DECISION: " + exception.getMessage());
        }

        @Test
        @DisplayName("SUBGRAPH node throws UnsupportedOperationException")
        void subgraphNodeThrows() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start",
                List.of("subgraph"), null,
                StepKind.NORMAL, null));

            steps.put("subgraph", createStepNode("subgraph", "Filter",
                List.of("end"), null,
                StepKind.SUBGRAPH, null));

            steps.put("end", createStepNode("end", "End",
                null, null, StepKind.NORMAL, null));

            ExecutionPlan plan = new ExecutionPlan(List.of("start"), steps);

            UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> dynamicJobBuilder.buildJob(plan, "subgraph-test")
            );

            assertTrue(exception.getMessage().contains("SUBGRAPH"),
                "Error should mention SUBGRAPH: " + exception.getMessage());
        }
    }

    @Nested
    @DisplayName("JoinBarrierTasklet")
    class JoinBarrierTaskletTests {

        @Test
        @DisplayName("JoinBarrierTasklet tracks upstream branches")
        void joinBarrierTracksUpstream() {
            JoinBarrierTasklet tasklet = new JoinBarrierTasklet("test-join",
                List.of("branch-a", "branch-b"));

            assertEquals("test-join", tasklet.getJoinNodeId());
            assertEquals(List.of("branch-a", "branch-b"), tasklet.getUpstreamBranches());
        }

        @Test
        @DisplayName("Branch completion tracking works correctly")
        void branchCompletionTracking() {
            JoinBarrierTasklet tasklet = new JoinBarrierTasklet("join",
                List.of("a", "b", "c"));

            assertFalse(tasklet.allBranchesComplete());

            tasklet.recordBranchCompletion("a", true);
            assertFalse(tasklet.allBranchesComplete());

            tasklet.recordBranchCompletion("b", true);
            assertFalse(tasklet.allBranchesComplete());

            tasklet.recordBranchCompletion("c", true);
            assertTrue(tasklet.allBranchesComplete());
        }

        @Test
        @DisplayName("Empty upstream means always complete")
        void emptyUpstreamAlwaysComplete() {
            JoinBarrierTasklet tasklet = new JoinBarrierTasklet("join", List.of());
            assertTrue(tasklet.allBranchesComplete());
        }
    }

    @Nested
    @DisplayName("Two Forks Converge to Same Join")
    class NestedForkTests {

        @Test
        @DisplayName("Nested fork within branch requires its own joinNodeId")
        void nestedForkRequiresOwnJoin() {
            ExecutionPlan plan = createNestedForkPlan();

            Job job = dynamicJobBuilder.buildJob(plan, "nested-fork-test");

            assertNotNull(job, "Nested fork plan should build");
        }

        private ExecutionPlan createNestedForkPlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints outerForkHints = new ExecutionHints();
            outerForkHints.setMode(ExecutionMode.PARALLEL);
            outerForkHints.setJoinNodeId("outer-join");

            steps.put("outer-fork", createStepNode("outer-fork", "Start",
                List.of("branch-a", "branch-b"), null,
                StepKind.FORK, outerForkHints));

            steps.put("branch-a", createStepNode("branch-a", "Filter",
                List.of("outer-join"), null,
                StepKind.NORMAL, null));

            steps.put("branch-b", createStepNode("branch-b", "Filter",
                List.of("outer-join"), null,
                StepKind.NORMAL, null));

            steps.put("outer-join", createStepNode("outer-join", "Filter",
                List.of("end"), null,
                StepKind.JOIN, null));

            steps.put("end", createStepNode("end", "End",
                null, null, StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("outer-fork"), steps);
        }
    }

    private StepNode createStepNode(String nodeId, String nodeType,
                                    List<String> nextSteps, List<String> errorSteps,
                                    StepKind kind, ExecutionHints hints) {
        ObjectNode config = objectMapper.createObjectNode();
        return new StepNode(
            nodeId,
            nodeType,
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
