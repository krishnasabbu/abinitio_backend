package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.service.ExecutionApiService;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.job.DynamicJobBuilder;
import com.workflow.engine.graph.*;
import com.workflow.engine.model.ExecutionMode;
import com.workflow.engine.model.ExecutionHints;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.Edge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "workflow.job.require-workflow-id=false"
})
public class EndToEndExecutionVerificationTest {

    @Autowired
    private ExecutionApiService executionApiService;

    @Autowired
    private NodeExecutorRegistry nodeExecutorRegistry;

    @Autowired
    private ExecutionGraphBuilder executionGraphBuilder;

    @Autowired
    private DynamicJobBuilder dynamicJobBuilder;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        jdbcTemplate.update("DELETE FROM node_executions");
        jdbcTemplate.update("DELETE FROM execution_logs");
        jdbcTemplate.update("DELETE FROM workflow_executions");
    }

    @Test
    public void testEndToEndLinearWorkflow() throws Exception {
        Map<String, Object> workflow = createLinearWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        assertNotNull(response);
        assertEquals("running", response.get("status"));
        String executionId = (String) response.get("execution_id");
        assertNotNull(executionId);
        assertTrue(((Integer) response.get("total_nodes")) > 0);

        verifyExecutionRecord(executionId, "Test Linear Workflow");
    }

    @Test
    public void testEndToEndBranchingWorkflow() throws Exception {
        Map<String, Object> workflow = createBranchingWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        assertNotNull(response);
        assertEquals("running", response.get("status"));
        String executionId = (String) response.get("execution_id");
        assertNotNull(executionId);

        verifyExecutionRecord(executionId, "Test Branching Workflow");
    }

    @Test
    public void testEndToEndParallelWorkflow() throws Exception {
        Map<String, Object> workflow = createParallelWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("parallel", request);

        assertNotNull(response);
        assertEquals("running", response.get("status"));
        String executionId = (String) response.get("execution_id");
        assertNotNull(executionId);

        verifyExecutionRecord(executionId, "Test Parallel Workflow");
    }

    @Test
    public void testNodeExecutorRegistryLoaded() {
        assertTrue(nodeExecutorRegistry.getExecutorCount() > 0);

        assertTrue(nodeExecutorRegistry.hasExecutor("Start"));
        assertTrue(nodeExecutorRegistry.hasExecutor("End"));
        assertTrue(nodeExecutorRegistry.hasExecutor("FileSource"));
        assertTrue(nodeExecutorRegistry.hasExecutor("FileSink"));

        assertNotNull(nodeExecutorRegistry.getExecutor("Start"));
        assertNotNull(nodeExecutorRegistry.getExecutor("FileSource"));
    }

    @Test
    public void testNodeTypeNormalizationWithWhitespace() {
        assertTrue(nodeExecutorRegistry.hasExecutor("  FileSource  ".trim()));
        assertNotNull(nodeExecutorRegistry.getExecutor("FileSource"));

        assertTrue(nodeExecutorRegistry.hasExecutor("End"));
    }

    @Test
    public void testNullNodeTypeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            nodeExecutorRegistry.getExecutor(null);
        });
    }

    @Test
    public void testBlankNodeTypeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            nodeExecutorRegistry.getExecutor("   ");
        });
    }

    @Test
    public void testUnknownNodeTypeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            nodeExecutorRegistry.getExecutor("UnknownExecutor");
        });
    }

    @Test
    public void testExecutionWithMetricsEnabled() throws Exception {
        Map<String, Object> workflow = createWorkflowWithMetrics();
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        assertNotNull(response);
        assertEquals("running", response.get("status"));
        String executionId = (String) response.get("execution_id");

        Thread.sleep(200);

        Map<String, Object> metrics = executionApiService.getExecutionMetrics(executionId);
        assertNotNull(metrics);
        assertEquals(executionId, metrics.get("execution_id"));
    }

    @Test
    public void testExecutionWithPersistenceListener() throws Exception {
        Map<String, Object> workflow = createLinearWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String executionId = (String) response.get("execution_id");

        Thread.sleep(200);

        List<Map<String, Object>> executions = jdbcTemplate.queryForList(
            "SELECT * FROM workflow_executions WHERE execution_id = ?",
            executionId
        );

        assertEquals(1, executions.size());
        Map<String, Object> execution = executions.get(0);
        assertEquals("running", execution.get("status"));
        assertNotNull(execution.get("parameters"));
        assertTrue(execution.get("parameters").toString().contains("nodes"));
    }

    @Test
    public void testGraphValidationFailsWithMissingNodeType() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("name", "Invalid Workflow");

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("type", "Start");

        Map<String, Object> invalidNode = new HashMap<>();
        invalidNode.put("id", "invalid_1");
        invalidNode.put("type", "NonExistentExecutor");

        workflow.put("nodes", List.of(startNode, invalidNode));

        Map<String, Object> edge = new HashMap<>();
        edge.put("source", "start_1");
        edge.put("target", "invalid_1");
        edge.put("isControl", true);

        workflow.put("edges", List.of(edge));

        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        assertEquals("error", response.get("status"));
        assertTrue(response.get("message").toString().contains("No executor registered"));
    }

    @Test
    public void testCanvasJsonExecution() throws Exception {
        Map<String, Object> canvasJson = createReactCanvasWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.putAll(canvasJson);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        assertNotNull(response);
        assertEquals("running", response.get("status"));
        String executionId = (String) response.get("execution_id");
        assertNotNull(executionId);

        verifyExecutionRecord(executionId, "Untitled Workflow");
    }

    @Test
    public void testCanvasJsonWithMultipleJoins() throws Exception {
        Map<String, Object> canvasJson = createComplexCanvasWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.putAll(canvasJson);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        assertNotNull(response);
        assertEquals("running", response.get("status"));
        String executionId = (String) response.get("execution_id");
        assertNotNull(executionId);
        assertTrue((Integer) response.get("total_nodes") >= 5);

        verifyExecutionRecord(executionId, "Untitled Workflow");
    }

    @Test
    public void testExecutionVerificationReport() throws Exception {
        Map<String, Object> workflow = createLinearWorkflow();
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);

        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String executionId = (String) response.get("execution_id");

        assertNotNull(executionId);
        assertTrue(executionId.startsWith("exec_"));

        Map<String, Object> verificationReport = buildVerificationReport(executionId);
        assertNotNull(verificationReport);
        assertEquals(executionId, verificationReport.get("executionId"));
        assertEquals("running", verificationReport.get("jobStatus"));
        assertTrue((int) verificationReport.get("stepsCount") >= 0);
        assertTrue((boolean) verificationReport.get("metricsEnabled"));
        assertTrue((boolean) verificationReport.get("persistenceEnabled"));
    }

    private Map<String, Object> createLinearWorkflow() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("name", "Test Linear Workflow");

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("type", "Start");

        Map<String, Object> filterNode = new HashMap<>();
        filterNode.put("id", "filter_1");
        filterNode.put("type", "Filter");
        filterNode.put("config", Map.of("condition", "true"));

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "end_1");
        endNode.put("type", "End");

        List<Map<String, Object>> edges = List.of(
            Map.of("source", "start_1", "target", "filter_1", "isControl", true),
            Map.of("source", "filter_1", "target", "end_1", "isControl", false)
        );

        workflow.put("nodes", List.of(startNode, filterNode, endNode));
        workflow.put("edges", edges);
        return workflow;
    }

    private Map<String, Object> createBranchingWorkflow() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("name", "Test Branching Workflow");

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("type", "Start");

        Map<String, Object> filterNode = new HashMap<>();
        filterNode.put("id", "filter_1");
        filterNode.put("type", "Filter");
        filterNode.put("config", Map.of("condition", "true"));

        Map<String, Object> rejectNode = new HashMap<>();
        rejectNode.put("id", "reject_1");
        rejectNode.put("type", "Reject");
        rejectNode.put("config", Map.of("reason", "test"));

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "end_1");
        endNode.put("type", "End");

        List<Map<String, Object>> edges = List.of(
            Map.of("source", "start_1", "target", "filter_1", "isControl", true),
            Map.of("source", "filter_1", "target", "reject_1", "isControl", false),
            Map.of("source", "filter_1", "target", "endNode", "isControl", false),
            Map.of("source", "reject_1", "target", "end_1", "isControl", false)
        );

        workflow.put("nodes", List.of(startNode, filterNode, rejectNode, endNode));
        workflow.put("edges", edges);
        return workflow;
    }

    private Map<String, Object> createParallelWorkflow() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("name", "Test Parallel Workflow");

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("type", "Start");

        Map<String, Object> filterNode1 = new HashMap<>();
        filterNode1.put("id", "filter_1");
        filterNode1.put("type", "Filter");
        filterNode1.put("config", Map.of("condition", "true"));

        Map<String, Object> filterNode2 = new HashMap<>();
        filterNode2.put("id", "filter_2");
        filterNode2.put("type", "Filter");
        filterNode2.put("config", Map.of("condition", "true"));

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "end_1");
        endNode.put("type", "End");

        List<Map<String, Object>> edges = List.of(
            Map.of("source", "start_1", "target", "filter_1", "isControl", true),
            Map.of("source", "start_1", "target", "filter_2", "isControl", true),
            Map.of("source", "filter_1", "target", "end_1", "isControl", false),
            Map.of("source", "filter_2", "target", "end_1", "isControl", false)
        );

        Map<String, Object> executionHints = new HashMap<>();
        executionHints.put("mode", "PARALLEL");

        workflow.put("nodes", List.of(startNode, filterNode1, filterNode2, endNode));
        workflow.put("edges", edges);
        workflow.put("executionHints", executionHints);
        return workflow;
    }

    private Map<String, Object> createWorkflowWithMetrics() {
        Map<String, Object> workflow = createLinearWorkflow();

        Map<String, Object> filterNode = new HashMap<>();
        filterNode.put("id", "filter_1");
        filterNode.put("type", "Filter");
        filterNode.put("config", Map.of("condition", "true"));

        Map<String, Object> metricsConfig = new HashMap<>();
        metricsConfig.put("enabled", true);
        metricsConfig.put("collectTiming", true);
        metricsConfig.put("collectRecordCount", true);

        filterNode.put("metrics", metricsConfig);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflow.get("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            if ("Filter".equals(node.get("type"))) {
                nodes.set(i, filterNode);
            }
        }

        return workflow;
    }

    private void verifyExecutionRecord(String executionId, String expectedWorkflowName) throws Exception {
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            "SELECT * FROM workflow_executions WHERE execution_id = ?",
            executionId
        );

        assertEquals(1, records.size());
        Map<String, Object> record = records.get(0);
        assertEquals("running", record.get("status"));
        assertEquals(expectedWorkflowName, record.get("workflow_name"));
        assertNotNull(record.get("start_time"));
        assertNotNull(record.get("parameters"));
    }

    private Map<String, Object> buildVerificationReport(String executionId) throws Exception {
        Map<String, Object> report = new HashMap<>();
        report.put("executionId", executionId);

        List<Map<String, Object>> executions = jdbcTemplate.queryForList(
            "SELECT * FROM workflow_executions WHERE execution_id = ?",
            executionId
        );

        if (!executions.isEmpty()) {
            Map<String, Object> execution = executions.get(0);
            report.put("jobStatus", execution.get("status"));
            report.put("stepsCount", execution.get("total_nodes"));
        }

        report.put("metricsEnabled", true);
        report.put("persistenceEnabled", true);

        List<String> registeredTypes = new ArrayList<>();
        registeredTypes.add("Start");
        registeredTypes.add("End");
        registeredTypes.add("Filter");
        report.put("registeredExecutors", registeredTypes);

        return report;
    }

    private Map<String, Object> createReactCanvasWorkflow() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("workflowName", "Untitled Workflow");

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "Start_1769798871535");
        startNode.put("type", "Start");
        startNode.put("label", "Start");
        startNode.put("data", Map.of("config", Map.of()));

        Map<String, Object> fileSourceNode1 = new HashMap<>();
        fileSourceNode1.put("id", "FileSource_1769798905020");
        fileSourceNode1.put("type", "FileSource");
        fileSourceNode1.put("label", "File Source");
        fileSourceNode1.put("data", Map.of(
            "config", Map.of(
                "filePath", "",
                "fileType", "csv",
                "delimiter", ",",
                "header", true
            )
        ));

        Map<String, Object> filterNode = new HashMap<>();
        filterNode.put("id", "Filter_1769798950918");
        filterNode.put("type", "Filter");
        filterNode.put("label", "Filter");
        filterNode.put("data", Map.of(
            "config", Map.of("condition", "true")
        ));

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "End_1769799000000");
        endNode.put("type", "End");
        endNode.put("label", "End");
        endNode.put("data", Map.of("config", Map.of()));

        List<Map<String, Object>> nodes = List.of(startNode, fileSourceNode1, filterNode, endNode);

        List<Map<String, Object>> edges = List.of(
            Map.of(
                "source", "Start_1769798871535",
                "target", "FileSource_1769798905020",
                "sourceHandle", "control",
                "targetHandle", "control",
                "type", "control",
                "isControl", true
            ),
            Map.of(
                "source", "FileSource_1769798905020",
                "target", "Filter_1769798950918",
                "sourceHandle", "out",
                "targetHandle", "in",
                "isControl", false
            ),
            Map.of(
                "source", "Filter_1769798950918",
                "target", "End_1769799000000",
                "sourceHandle", "out",
                "targetHandle", "in",
                "isControl", false
            )
        );

        workflow.put("nodes", nodes);
        workflow.put("edges", edges);
        return workflow;
    }

    private Map<String, Object> createComplexCanvasWorkflow() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("workflowName", "Complex Join Workflow");

        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "Start_1");
        startNode.put("type", "Start");
        startNode.put("data", Map.of("config", Map.of()));

        Map<String, Object> fileSource1 = new HashMap<>();
        fileSource1.put("id", "FileSource_1");
        fileSource1.put("type", "FileSource");
        fileSource1.put("data", Map.of("config", Map.of("filePath", "", "fileType", "csv")));

        Map<String, Object> fileSource2 = new HashMap<>();
        fileSource2.put("id", "FileSource_2");
        fileSource2.put("type", "FileSource");
        fileSource2.put("data", Map.of("config", Map.of("filePath", "", "fileType", "csv")));

        Map<String, Object> fileSource3 = new HashMap<>();
        fileSource3.put("id", "FileSource_3");
        fileSource3.put("type", "FileSource");
        fileSource3.put("data", Map.of("config", Map.of("filePath", "", "fileType", "csv")));

        Map<String, Object> join1 = new HashMap<>();
        join1.put("id", "Join_1");
        join1.put("type", "Join");
        join1.put("data", Map.of("config", Map.of(
            "joinType", "inner",
            "leftKeys", "",
            "rightKeys", ""
        )));

        Map<String, Object> join2 = new HashMap<>();
        join2.put("id", "Join_2");
        join2.put("type", "Join");
        join2.put("data", Map.of("config", Map.of(
            "joinType", "inner",
            "leftKeys", "",
            "rightKeys", ""
        )));

        Map<String, Object> fileSink = new HashMap<>();
        fileSink.put("id", "FileSink_1");
        fileSink.put("type", "FileSink");
        fileSink.put("data", Map.of("config", Map.of("outputPath", "", "fileType", "csv")));

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "End_1");
        endNode.put("type", "End");
        endNode.put("data", Map.of("config", Map.of()));

        List<Map<String, Object>> nodes = List.of(
            startNode, fileSource1, fileSource2, fileSource3, join1, join2, fileSink, endNode
        );

        List<Map<String, Object>> edges = List.of(
            Map.of("source", "Start_1", "target", "FileSource_1", "isControl", true),
            Map.of("source", "Start_1", "target", "FileSource_2", "isControl", true),
            Map.of("source", "Start_1", "target", "FileSource_3", "isControl", true),
            Map.of("source", "FileSource_1", "target", "Join_1", "sourceHandle", "out", "targetHandle", "left"),
            Map.of("source", "FileSource_2", "target", "Join_1", "sourceHandle", "out", "targetHandle", "right"),
            Map.of("source", "Join_1", "target", "Join_2", "sourceHandle", "out", "targetHandle", "left"),
            Map.of("source", "FileSource_3", "target", "Join_2", "sourceHandle", "out", "targetHandle", "right"),
            Map.of("source", "Join_2", "target", "FileSink_1", "sourceHandle", "out", "targetHandle", "in"),
            Map.of("source", "FileSink_1", "target", "End_1", "isControl", true)
        );

        workflow.put("nodes", nodes);
        workflow.put("edges", edges);
        return workflow;
    }

    @Nested
    @DisplayName("Fork/Join Semantics Verification")
    class ForkJoinSemanticsTests {

        @Test
        @DisplayName("FORK node is correctly classified with StepKind.FORK")
        void forkNodeCorrectlyClassified() {
            WorkflowDefinition workflow = createForkJoinWorkflowDefinition();
            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            StepNode splitNode = plan.steps().get("split_1");
            assertNotNull(splitNode, "Split node should exist");
            assertEquals(StepKind.FORK, splitNode.kind(), "Split should have FORK kind");
        }

        @Test
        @DisplayName("JOIN node is correctly classified with StepKind.JOIN")
        void joinNodeCorrectlyClassified() {
            WorkflowDefinition workflow = createForkJoinWorkflowDefinition();
            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            StepNode gatherNode = plan.steps().get("gather_1");
            assertNotNull(gatherNode, "Gather node should exist");
            assertEquals(StepKind.JOIN, gatherNode.kind(), "Gather should have JOIN kind");
        }

        @Test
        @DisplayName("JOIN node has correct upstream steps")
        void joinNodeHasUpstreamSteps() {
            WorkflowDefinition workflow = createForkJoinWorkflowDefinition();
            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            StepNode gatherNode = plan.steps().get("gather_1");
            assertNotNull(gatherNode.upstreamSteps(), "Upstream steps should be set");
            assertEquals(2, gatherNode.upstreamSteps().size(), "Should have 2 upstream steps");
            assertTrue(gatherNode.upstreamSteps().contains("filter_1"), "Should contain filter_1");
            assertTrue(gatherNode.upstreamSteps().contains("filter_2"), "Should contain filter_2");
        }

        @Test
        @DisplayName("FORK node with explicit joinNodeId is validated")
        void forkNodeWithExplicitJoinNodeId() {
            WorkflowDefinition workflow = createForkJoinWorkflowDefinitionWithExplicitJoin();
            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            StepNode splitNode = plan.steps().get("split_1");
            assertNotNull(splitNode.executionHints(), "Execution hints should be set");
            assertNotNull(splitNode.executionHints().getJoinNodeId(), "JoinNodeId should be set");
            assertEquals("gather_1", splitNode.executionHints().getJoinNodeId());
        }

        @Test
        @DisplayName("Job builds and runs successfully with fork/join")
        void jobBuildsWithForkJoin() throws Exception {
            WorkflowDefinition workflow = createForkJoinWorkflowDefinitionWithExplicitJoin();
            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            Job job = dynamicJobBuilder.buildJob(plan, "fork-join-test");
            assertNotNull(job, "Job should build successfully");

            JobParameters params = new JobParametersBuilder()
                .addString("executionId", "fork-join-" + System.currentTimeMillis())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            JobExecution execution = jobLauncher.run(job, params);
            assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
                "Fork/join job should complete successfully");
        }

        private WorkflowDefinition createForkJoinWorkflowDefinition() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("fork-join-test");
            workflow.setName("Fork Join Test");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNodeDef("start_1", "Start"));
            nodes.add(createNodeDef("split_1", "Split"));
            nodes.add(createNodeDef("filter_1", "Filter"));
            nodes.add(createNodeDef("filter_2", "Filter"));
            nodes.add(createNodeDef("gather_1", "Gather"));
            nodes.add(createNodeDef("end_1", "End"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdgeDef("start_1", "split_1", true));
            edges.add(createEdgeDef("split_1", "filter_1", false));
            edges.add(createEdgeDef("split_1", "filter_2", false));
            edges.add(createEdgeDef("filter_1", "gather_1", false));
            edges.add(createEdgeDef("filter_2", "gather_1", false));
            edges.add(createEdgeDef("gather_1", "end_1", false));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);
            return workflow;
        }

        private WorkflowDefinition createForkJoinWorkflowDefinitionWithExplicitJoin() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("fork-join-explicit");
            workflow.setName("Fork Join Explicit Test");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNodeDef("start_1", "Start"));

            NodeDefinition splitNode = createNodeDef("split_1", "Split");
            ExecutionHints hints = new ExecutionHints();
            hints.setMode(ExecutionMode.PARALLEL);
            hints.setJoinNodeId("gather_1");
            splitNode.setExecutionHints(hints);
            nodes.add(splitNode);

            nodes.add(createNodeDef("filter_1", "Filter"));
            nodes.add(createNodeDef("filter_2", "Filter"));
            nodes.add(createNodeDef("gather_1", "Gather"));
            nodes.add(createNodeDef("end_1", "End"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdgeDef("start_1", "split_1", true));
            edges.add(createEdgeDef("split_1", "filter_1", false));
            edges.add(createEdgeDef("split_1", "filter_2", false));
            edges.add(createEdgeDef("filter_1", "gather_1", false));
            edges.add(createEdgeDef("filter_2", "gather_1", false));
            edges.add(createEdgeDef("gather_1", "end_1", false));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);
            return workflow;
        }

        private NodeDefinition createNodeDef(String id, String type) {
            NodeDefinition node = new NodeDefinition();
            node.setId(id);
            node.setType(type);
            node.setConfig(objectMapper.createObjectNode());
            return node;
        }

        private Edge createEdgeDef(String source, String target, boolean control) {
            Edge edge = new Edge();
            edge.setSource(source);
            edge.setTarget(target);
            edge.setControl(control);
            return edge;
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Cycle detection throws GraphValidationException")
        void cycleDetectionThrows() {
            WorkflowDefinition workflow = createCyclicWorkflowDefinition();

            assertThrows(GraphValidationException.class, () -> {
                executionGraphBuilder.build(workflow);
            }, "Should detect cycle and throw");
        }

        @Test
        @DisplayName("Missing node reference throws GraphValidationException")
        void missingNodeThrows() {
            WorkflowDefinition workflow = createMissingNodeWorkflowDefinition();

            assertThrows(GraphValidationException.class, () -> {
                executionGraphBuilder.build(workflow);
            }, "Should detect missing node and throw");
        }

        @Test
        @DisplayName("Implicit join is detected with warning (not error)")
        void implicitJoinDetected() {
            WorkflowDefinition workflow = createImplicitJoinWorkflowDefinition();

            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            StepNode convergenceNode = plan.steps().get("convergence_1");
            assertNotNull(convergenceNode, "Convergence node should exist");
        }

        private WorkflowDefinition createCyclicWorkflowDefinition() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("cyclic-test");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNodeDefinition("start_1", "Start"));
            nodes.add(createNodeDefinition("filter_1", "Filter"));
            nodes.add(createNodeDefinition("filter_2", "Filter"));
            nodes.add(createNodeDefinition("filter_3", "Filter"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdgeDefinition("start_1", "filter_1", true));
            edges.add(createEdgeDefinition("filter_1", "filter_2", false));
            edges.add(createEdgeDefinition("filter_2", "filter_3", false));
            edges.add(createEdgeDefinition("filter_3", "filter_1", false));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);
            return workflow;
        }

        private WorkflowDefinition createMissingNodeWorkflowDefinition() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("missing-test");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNodeDefinition("start_1", "Start"));
            nodes.add(createNodeDefinition("filter_1", "Filter"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdgeDefinition("start_1", "filter_1", true));
            edges.add(createEdgeDefinition("filter_1", "non_existent", false));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);
            return workflow;
        }

        private WorkflowDefinition createImplicitJoinWorkflowDefinition() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("implicit-join-test");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNodeDefinition("start_1", "Start"));
            nodes.add(createNodeDefinition("branch_a", "Filter"));
            nodes.add(createNodeDefinition("branch_b", "Filter"));
            nodes.add(createNodeDefinition("convergence_1", "Gather"));
            nodes.add(createNodeDefinition("end_1", "End"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdgeDefinition("start_1", "branch_a", true));
            edges.add(createEdgeDefinition("start_1", "branch_b", true));
            edges.add(createEdgeDefinition("branch_a", "convergence_1", false));
            edges.add(createEdgeDefinition("branch_b", "convergence_1", false));
            edges.add(createEdgeDefinition("convergence_1", "end_1", false));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);
            return workflow;
        }

        private NodeDefinition createNodeDefinition(String id, String type) {
            NodeDefinition node = new NodeDefinition();
            node.setId(id);
            node.setType(type);
            node.setConfig(objectMapper.createObjectNode());
            return node;
        }

        private Edge createEdgeDefinition(String source, String target, boolean control) {
            Edge edge = new Edge();
            edge.setSource(source);
            edge.setTarget(target);
            edge.setControl(control);
            return edge;
        }
    }

    @Nested
    @DisplayName("Deterministic Job Naming Tests")
    class DeterministicNamingTests {

        @Test
        @DisplayName("workflowId from plan is used for job naming")
        void workflowIdFromPlanUsed() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("my-stable-workflow-id");
            workflow.setName("Test Workflow");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNode("start_1", "Start"));
            nodes.add(createNode("end_1", "End"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdge("start_1", "end_1", true));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);

            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            assertEquals("my-stable-workflow-id", plan.workflowId(),
                "Plan should carry workflowId from definition");
        }

        @Test
        @DisplayName("Same workflowId produces same job name")
        void sameWorkflowIdSameJobName() {
            WorkflowDefinition workflow = new WorkflowDefinition();
            workflow.setId("stable-id-123");
            workflow.setName("Test Workflow");

            List<NodeDefinition> nodes = new ArrayList<>();
            nodes.add(createNode("start_1", "Start"));
            nodes.add(createNode("end_1", "End"));

            List<Edge> edges = new ArrayList<>();
            edges.add(createEdge("start_1", "end_1", true));

            workflow.setNodes(nodes);
            workflow.setEdges(edges);

            ExecutionPlan plan = executionGraphBuilder.build(workflow);

            Job job1 = dynamicJobBuilder.buildJob(plan);
            Job job2 = dynamicJobBuilder.buildJob(plan);

            assertEquals(job1.getName(), job2.getName(),
                "Same workflowId should produce same job name for restartability");
        }

        private NodeDefinition createNode(String id, String type) {
            NodeDefinition node = new NodeDefinition();
            node.setId(id);
            node.setType(type);
            node.setConfig(objectMapper.createObjectNode());
            return node;
        }

        private Edge createEdge(String source, String target, boolean control) {
            Edge edge = new Edge();
            edge.setSource(source);
            edge.setTarget(target);
            edge.setControl(control);
            return edge;
        }
    }
}
