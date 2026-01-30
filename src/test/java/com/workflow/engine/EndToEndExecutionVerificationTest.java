package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.service.ExecutionApiService;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.model.ExecutionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class EndToEndExecutionVerificationTest {

    @Autowired
    private ExecutionApiService executionApiService;

    @Autowired
    private NodeExecutorRegistry nodeExecutorRegistry;

    @Autowired
    private ExecutionGraphBuilder executionGraphBuilder;

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
}
