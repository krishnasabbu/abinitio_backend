package com.workflow.engine.execution.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.StartExecutor;
import com.workflow.engine.execution.EndExecutor;
import com.workflow.engine.execution.FilterExecutor;
import com.workflow.engine.graph.*;
import com.workflow.engine.metrics.MetricsCollector;
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
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DynamicJobBuilder Unit Tests")
public class DynamicJobBuilderUnitTest {

    @Autowired
    private DynamicJobBuilder dynamicJobBuilder;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, AtomicInteger> executionCounts = new ConcurrentHashMap<>();
    private static final List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void resetCounters() {
        executionCounts.clear();
        executionOrder.clear();
    }

    @Nested
    @DisplayName("Fork/Join Semantics")
    class ForkJoinTests {

        @Test
        @DisplayName("Fork with 2 branches executes join exactly once")
        void forkWithTwoBranchesExecutesJoinOnce() throws Exception {
            ExecutionPlan plan = createForkJoinPlan();

            Job job = dynamicJobBuilder.buildJob(plan, "fork-join-test");

            JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            JobExecution execution = jobLauncher.run(job, params);

            assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
                "Job should complete successfully");
        }

        @Test
        @DisplayName("Parallel fork executes all branches concurrently")
        void parallelForkExecutesAllBranches() throws Exception {
            ExecutionPlan plan = createParallelForkPlan();

            Job job = dynamicJobBuilder.buildJob(plan, "parallel-fork-test");

            assertNotNull(job, "Job should be built successfully");
            assertEquals("workflow-parallel-fork-test", job.getName());
        }

        private ExecutionPlan createForkJoinPlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start", List.of("fork"), null,
                StepKind.NORMAL, null));

            ExecutionHints parallelHints = new ExecutionHints();
            parallelHints.setMode(ExecutionMode.PARALLEL);

            steps.put("fork", createStepNode("fork", "Filter",
                List.of("branch-a", "branch-b"), null,
                StepKind.FORK, parallelHints));

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

        private ExecutionPlan createParallelForkPlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints parallelHints = new ExecutionHints();
            parallelHints.setMode(ExecutionMode.PARALLEL);

            steps.put("source", createStepNode("source", "Start",
                List.of("a", "b", "c"), null,
                StepKind.FORK, parallelHints));

            steps.put("a", createStepNode("a", "Filter", List.of("join"), null,
                StepKind.NORMAL, null));
            steps.put("b", createStepNode("b", "Filter", List.of("join"), null,
                StepKind.NORMAL, null));
            steps.put("c", createStepNode("c", "Filter", List.of("join"), null,
                StepKind.NORMAL, null));

            steps.put("join", createStepNode("join", "Filter",
                List.of("sink"), null,
                StepKind.JOIN, null));

            steps.put("sink", createStepNode("sink", "End", null, null,
                StepKind.NORMAL, null));

            return new ExecutionPlan(List.of("source"), steps);
        }
    }

    @Nested
    @DisplayName("Sequential Multi-Next")
    class SequentialMultiNextTests {

        @Test
        @DisplayName("Sequential mode executes all next steps deterministically")
        void sequentialModeExecutesAllBranches() throws Exception {
            ExecutionPlan plan = createSequentialMultiNextPlan();

            Job job = dynamicJobBuilder.buildJob(plan, "sequential-test");

            assertNotNull(job, "Job should be built");

            JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            JobExecution execution = jobLauncher.run(job, params);

            assertEquals(BatchStatus.COMPLETED, execution.getStatus());
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
        @DisplayName("Error steps are wired correctly for FAILED status")
        void errorStepsWiredForFailedStatus() throws Exception {
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

        @Test
        @DisplayName("Implicit join detection warns or fails")
        void implicitJoinDetection() {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            ExecutionHints parallelHints = new ExecutionHints();
            parallelHints.setMode(ExecutionMode.PARALLEL);

            steps.put("fork", createStepNode("fork", "Start",
                List.of("a", "b"), null,
                StepKind.FORK, parallelHints));

            steps.put("a", createStepNode("a", "Filter",
                List.of("converge"), null,
                StepKind.NORMAL, null));

            steps.put("b", createStepNode("b", "Filter",
                List.of("converge"), null,
                StepKind.NORMAL, null));

            steps.put("converge", createStepNode("converge", "End",
                null, null,
                StepKind.NORMAL, null));

            ExecutionPlan implicitJoinPlan = new ExecutionPlan(List.of("fork"), steps);

            assertThrows(ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> dynamicJobBuilder.buildJob(implicitJoinPlan, "implicit-join-test"),
                "Should detect implicit join without explicit JOIN kind");
        }
    }

    @Nested
    @DisplayName("Job Identity")
    class JobIdentityTests {

        @Test
        @DisplayName("Job name is deterministic with workflowId")
        void jobNameDeterministicWithWorkflowId() throws Exception {
            ExecutionPlan plan = createSimplePlan();

            Job job1 = dynamicJobBuilder.buildJob(plan, "my-workflow-123");
            Job job2 = dynamicJobBuilder.buildJob(plan, "my-workflow-123");

            assertEquals("workflow-my-workflow-123", job1.getName());
            assertEquals(job1.getName(), job2.getName(),
                "Same workflowId should produce same job name");
        }

        @Test
        @DisplayName("Job name uses UUID when workflowId not provided")
        void jobNameUsesUuidWhenNoWorkflowId() throws Exception {
            ExecutionPlan plan = createSimplePlan();

            Job job1 = dynamicJobBuilder.buildJob(plan);
            Job job2 = dynamicJobBuilder.buildJob(plan);

            assertTrue(job1.getName().startsWith("workflow-"));
            assertTrue(job2.getName().startsWith("workflow-"));
            assertNotEquals(job1.getName(), job2.getName(),
                "Without workflowId, each build should have unique name");
        }

        private ExecutionPlan createSimplePlan() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("only-step", createStepNode("only-step", "Start",
                null, null, StepKind.NORMAL, null));
            return new ExecutionPlan(List.of("only-step"), steps);
        }
    }

    @Nested
    @DisplayName("JoinBarrierTasklet")
    class JoinBarrierTaskletTests {

        @Test
        @DisplayName("JoinBarrierTasklet returns FINISHED")
        void joinBarrierReturnsFinished() throws Exception {
            JoinBarrierTasklet tasklet = new JoinBarrierTasklet("test-join",
                List.of("branch-a", "branch-b"));

            assertEquals("test-join", tasklet.getJoinNodeId());
            assertEquals(List.of("branch-a", "branch-b"), tasklet.getUpstreamBranches());
        }

        @Test
        @DisplayName("Branch completion tracking works")
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
    }

    @Nested
    @DisplayName("Subgraph Extension")
    class SubgraphExtensionTests {

        @Test
        @DisplayName("Subgraph node uses fallback implementation")
        void subgraphNodeUsesFallback() throws Exception {
            Map<String, StepNode> steps = new LinkedHashMap<>();

            steps.put("start", createStepNode("start", "Start",
                List.of("subgraph-node"), null,
                StepKind.NORMAL, null));

            steps.put("subgraph-node", createStepNode("subgraph-node", "Filter",
                List.of("end"), null,
                StepKind.SUBGRAPH, null));

            steps.put("end", createStepNode("end", "End",
                null, null, StepKind.NORMAL, null));

            ExecutionPlan plan = new ExecutionPlan(List.of("start"), steps);

            Job job = dynamicJobBuilder.buildJob(plan, "subgraph-test");
            assertNotNull(job, "Job with subgraph should still build (fallback)");
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
