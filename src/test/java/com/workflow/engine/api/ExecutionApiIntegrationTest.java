package com.workflow.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.service.ExecutionApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ExecutionApiIntegrationTest {

    @Autowired
    private ExecutionApiService executionApiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        // Clean up test data
        jdbcTemplate.update("DELETE FROM node_executions");
        jdbcTemplate.update("DELETE FROM execution_logs");
        jdbcTemplate.update("DELETE FROM workflow_executions");
    }

    @Test
    public void testExecuteWorkflowCreatesExecutionRecord() throws Exception {
        // Arrange
        Map<String, Object> request = createSimpleWorkflowRequest();

        // Act
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        // Assert
        assertNotNull(response);
        assertEquals("running", response.get("status"));
        assertNotNull(response.get("execution_id"));

        // Verify database record was created
        String executionId = (String) response.get("execution_id");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT * FROM workflow_executions WHERE execution_id = ?",
                executionId
        );

        assertEquals(1, records.size());
        Map<String, Object> record = records.get(0);
        assertEquals("running", record.get("status"));
        assertEquals("Test Workflow", record.get("workflow_name"));
        assertEquals("sequential", record.get("execution_mode"));
        assertNotNull(record.get("start_time"));
    }

    @Test
    public void testExecuteWorkflowStoresWorkflowPayload() throws Exception {
        // Arrange
        Map<String, Object> request = createSimpleWorkflowRequest();

        // Act
        Map<String, Object> response = executionApiService.executeWorkflow("parallel", request);
        String executionId = (String) response.get("execution_id");

        // Assert
        String payload = jdbcTemplate.queryForObject(
                "SELECT parameters FROM workflow_executions WHERE execution_id = ?",
                String.class,
                executionId
        );

        assertNotNull(payload);
        assertTrue(payload.contains("\"nodes\""));
        assertTrue(payload.contains("\"edges\""));
    }

    @Test
    public void testGetExecutionHistory() throws Exception {
        // Arrange - execute a workflow
        Map<String, Object> request = createSimpleWorkflowRequest();
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String executionId = (String) response.get("execution_id");

        // Give it a moment for listeners to process
        Thread.sleep(100);

        // Act
        List<?> executions = executionApiService.getExecutionHistory(null);

        // Assert
        assertTrue(executions.size() > 0);
    }

    @Test
    public void testGetExecutionById() throws Exception {
        // Arrange - execute a workflow
        Map<String, Object> request = createSimpleWorkflowRequest();
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String executionId = (String) response.get("execution_id");

        // Give it a moment for listeners to process
        Thread.sleep(100);

        // Act
        var execution = executionApiService.getExecutionById(executionId);

        // Assert
        assertNotNull(execution);
        assertEquals(executionId, execution.getExecutionId());
        assertEquals("running", execution.getStatus());
    }

    @Test
    public void testCancelExecution() throws Exception {
        // Arrange - execute a workflow
        Map<String, Object> request = createSimpleWorkflowRequest();
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String executionId = (String) response.get("execution_id");

        // Act
        Map<String, Object> cancelResponse = executionApiService.cancelExecution(executionId);

        // Assert
        assertEquals("cancelled", cancelResponse.get("status"));

        // Verify database was updated
        var execution = executionApiService.getExecutionById(executionId);
        assertEquals("cancelled", execution.getStatus());
        assertNotNull(execution.getEndTime());
    }

    @Test
    public void testCancelExecutionNotFound() throws Exception {
        // Act
        Map<String, Object> response = executionApiService.cancelExecution("nonexistent");

        // Assert
        assertEquals("error", response.get("status"));
    }

    @Test
    public void testRerunExecution() throws Exception {
        // Arrange - execute a workflow
        Map<String, Object> request = createSimpleWorkflowRequest();
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String originalExecutionId = (String) response.get("execution_id");

        // Give it a moment for listeners to process
        Thread.sleep(100);

        // Act
        Map<String, Object> rerunResponse = executionApiService.rerunExecution(originalExecutionId, null);

        // Assert
        assertEquals("running", rerunResponse.get("status"));
        assertNotNull(rerunResponse.get("new_execution_id"));
        assertNotEquals(originalExecutionId, rerunResponse.get("new_execution_id"));

        // Verify new execution record was created
        String newExecutionId = (String) rerunResponse.get("new_execution_id");
        var newExecution = executionApiService.getExecutionById(newExecutionId);
        assertNotNull(newExecution);
        assertEquals("running", newExecution.getStatus());
    }

    @Test
    public void testRerunExecutionNotFound() throws Exception {
        // Act
        Map<String, Object> response = executionApiService.rerunExecution("nonexistent", null);

        // Assert
        assertEquals("error", response.get("status"));
    }

    @Test
    public void testGetExecutionMetrics() throws Exception {
        // Arrange - execute a workflow
        Map<String, Object> request = createSimpleWorkflowRequest();
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);
        String executionId = (String) response.get("execution_id");

        // Give it a moment for listeners to process
        Thread.sleep(100);

        // Act
        Map<String, Object> metrics = executionApiService.getExecutionMetrics(executionId);

        // Assert
        assertNotNull(metrics);
        assertEquals(executionId, metrics.get("execution_id"));
        assertNotNull(metrics.get("total_duration_ms"));
        assertNotNull(metrics.get("total_records"));
    }

    @Test
    public void testGetRecentExecutions() throws Exception {
        // Arrange - execute multiple workflows
        for (int i = 0; i < 3; i++) {
            Map<String, Object> request = createSimpleWorkflowRequest();
            executionApiService.executeWorkflow("sequential", request);
        }

        // Give it a moment for listeners to process
        Thread.sleep(100);

        // Act
        Map<String, Object> response = executionApiService.getRecentExecutions(10);

        // Assert
        assertNotNull(response);
        assertTrue(response.containsKey("executions"));
        List<?> executions = (List<?>) response.get("executions");
        assertTrue(executions.size() >= 3);
    }

    @Test
    public void testExecuteWorkflowWithInvalidWorkflow() throws Exception {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("workflow", null);

        // Act
        Map<String, Object> response = executionApiService.executeWorkflow("sequential", request);

        // Assert
        assertEquals("error", response.get("status"));
    }

    private Map<String, Object> createSimpleWorkflowRequest() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("name", "Test Workflow");
        workflow.put("description", "Test workflow for API integration");

        // Create a simple workflow with Start and End nodes
        Map<String, Object> startNode = new HashMap<>();
        startNode.put("id", "start_1");
        startNode.put("type", "Start");

        Map<String, Object> endNode = new HashMap<>();
        endNode.put("id", "end_1");
        endNode.put("type", "End");

        Map<String, Object> edge = new HashMap<>();
        edge.put("source", "start_1");
        edge.put("target", "end_1");
        edge.put("isControl", true);

        workflow.put("nodes", List.of(startNode, endNode));
        workflow.put("edges", List.of(edge));

        Map<String, Object> request = new HashMap<>();
        request.put("workflow", workflow);
        return request;
    }
}
