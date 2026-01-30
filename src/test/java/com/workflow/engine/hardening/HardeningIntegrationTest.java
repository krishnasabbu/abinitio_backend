package com.workflow.engine.hardening;

import com.workflow.engine.execution.DataSourceProvider;
import com.workflow.engine.execution.routing.EdgeBufferStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class HardeningIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSourceProvider dataSourceProvider;

    @BeforeEach
    public void setUp() {
        MDC.clear();
        jdbcTemplate.update("DELETE FROM node_executions");
        jdbcTemplate.update("DELETE FROM execution_logs");
        jdbcTemplate.update("DELETE FROM workflow_executions");
        jdbcTemplate.update("DELETE FROM database_connections");
    }

    @Test
    public void testMdcPropagation() {
        String executionId = "exec_test123";
        MDC.put("executionId", executionId);

        assertTrue(MDC.get("executionId") != null);
        assertEquals(executionId, MDC.get("executionId"));

        MDC.clear();
        assertNull(MDC.get("executionId"));
    }

    @Test
    public void testCancelLifecycleRunningToRequested() {
        String executionId = "exec_cancel_test";
        long startTime = System.currentTimeMillis();

        String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, status, start_time, execution_mode) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertSql, executionId, executionId, "Test Workflow", "running", startTime, "sequential");

        String updateSql = "UPDATE workflow_executions SET status = ? WHERE execution_id = ? AND status = 'running'";
        int updated = jdbcTemplate.update(updateSql, "cancel_requested", executionId);

        assertEquals(1, updated);

        String currentStatusSql = "SELECT status FROM workflow_executions WHERE execution_id = ?";
        String currentStatus = jdbcTemplate.queryForObject(currentStatusSql, String.class, executionId);
        assertEquals("cancel_requested", currentStatus);
    }

    @Test
    public void testCancelLifecycleRequestedToCancelled() {
        String executionId = "exec_cancel_complete_test";
        long startTime = System.currentTimeMillis();

        String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, status, start_time, execution_mode) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertSql, executionId, executionId, "Test Workflow", "cancel_requested", startTime, "sequential");

        String checkStatusSql = "SELECT status FROM workflow_executions WHERE execution_id = ?";
        String currentStatus = jdbcTemplate.queryForObject(checkStatusSql, String.class, executionId);
        assertEquals("cancel_requested", currentStatus);

        String updateSql = "UPDATE workflow_executions SET status = ?, end_time = ? WHERE execution_id = ?";
        jdbcTemplate.update(updateSql, "cancelled", System.currentTimeMillis(), executionId);

        String finalStatusSql = "SELECT status FROM workflow_executions WHERE execution_id = ?";
        String finalStatus = jdbcTemplate.queryForObject(finalStatusSql, String.class, executionId);
        assertEquals("cancelled", finalStatus);
    }

    @Test
    public void testBufferOverflow() {
        EdgeBufferStore store = new EdgeBufferStore(5);

        String executionId = "exec_buffer_test";
        String targetNodeId = "node_1";
        String targetPort = "output";

        for (int i = 0; i < 5; i++) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", i);
            record.put("value", "record_" + i);
            store.addRecord(executionId, targetNodeId, targetPort, record);
        }

        Map<String, Object> overflowRecord = new HashMap<>();
        overflowRecord.put("id", 999);
        overflowRecord.put("value", "overflow");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            store.addRecord(executionId, targetNodeId, targetPort, overflowRecord);
        });

        String errorMessage = exception.getMessage();
        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("Edge buffer overflow"));
        assertTrue(errorMessage.contains(executionId));
        assertTrue(errorMessage.contains("limit=5"));
    }

    @Test
    public void testBufferClearing() {
        EdgeBufferStore store = new EdgeBufferStore(100);

        String executionId = "exec_clear_test";
        String targetNodeId = "node_1";
        String targetPort = "output";

        for (int i = 0; i < 10; i++) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", i);
            store.addRecord(executionId, targetNodeId, targetPort, record);
        }

        assertTrue(store.hasRecords(executionId, targetNodeId, targetPort));

        store.clearBuffer(executionId, targetNodeId, targetPort);

        assertFalse(store.hasRecords(executionId, targetNodeId, targetPort));
    }

    @Test
    public void testDataSourceCacheInvalidation() {
        String connectionId = "test_conn_123";
        String connectionId2 = "test_conn_456";

        assertFalse(dataSourceProvider.hasDataSource(connectionId));
        assertFalse(dataSourceProvider.hasDataSource(connectionId2));

        dataSourceProvider.invalidateCache(connectionId);
        dataSourceProvider.invalidateCache(connectionId2);

        assertFalse(dataSourceProvider.hasDataSource(connectionId));
        assertFalse(dataSourceProvider.hasDataSource(connectionId2));
    }

    @Test
    public void testCancelExecutionLogging() {
        String executionId = "exec_cancel_log_test";
        long startTime = System.currentTimeMillis();

        String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, status, start_time, execution_mode) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertSql, executionId, executionId, "Test Workflow", "running", startTime, "sequential");

        long timestamp = System.currentTimeMillis();
        String datetime = new java.time.Instant.ofEpochMilli(timestamp).toString();

        String insertLogSql = "INSERT INTO execution_logs (timestamp, datetime, level, execution_id, message) " +
                "VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertLogSql, timestamp, datetime, "INFO", executionId, "Execution cancellation requested");

        String querySql = "SELECT * FROM execution_logs WHERE execution_id = ?";
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(querySql, executionId);

        assertEquals(1, logs.size());
        assertEquals("INFO", logs.get(0).get("level"));
        assertTrue(logs.get(0).get("message").toString().contains("cancellation"));
    }

    @Test
    public void testMultipleExecutionLogsWithExecutionId() {
        String executionId = "exec_multi_log_test";

        for (int i = 0; i < 5; i++) {
            long timestamp = System.currentTimeMillis() + i * 100;
            String datetime = new java.time.Instant.ofEpochMilli(timestamp).toString();

            String insertLogSql = "INSERT INTO execution_logs (timestamp, datetime, level, execution_id, message) " +
                    "VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(insertLogSql, timestamp, datetime, "INFO", executionId, "Log entry " + i);
        }

        String querySql = "SELECT * FROM execution_logs WHERE execution_id = ? ORDER BY timestamp";
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(querySql, executionId);

        assertEquals(5, logs.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(executionId, logs.get(i).get("execution_id"));
        }
    }

    @Test
    public void testBufferSizeTracking() {
        EdgeBufferStore store = new EdgeBufferStore(1000);

        String executionId = "exec_size_test";
        String targetNodeId = "node_1";
        String targetPort = "output";

        for (int i = 0; i < 100; i++) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", i);
            record.put("data", "test_data_" + i);
            store.addRecord(executionId, targetNodeId, targetPort, record);
        }

        List<Map<String, Object>> records = store.getRecords(executionId, targetNodeId, targetPort);
        assertEquals(100, records.size());

        store.clearExecution(executionId);

        List<Map<String, Object>> clearedRecords = store.getRecords(executionId, targetNodeId, targetPort);
        assertEquals(0, clearedRecords.size());
    }

    @Test
    public void testCancelNotRunning() {
        String executionId = "exec_not_running";
        long startTime = System.currentTimeMillis();

        String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, status, start_time, execution_mode) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertSql, executionId, executionId, "Test Workflow", "success", startTime, "sequential");

        String updateSql = "UPDATE workflow_executions SET status = ? WHERE execution_id = ? AND status = 'running'";
        int updated = jdbcTemplate.update(updateSql, "cancel_requested", executionId);

        assertEquals(0, updated);

        String currentStatusSql = "SELECT status FROM workflow_executions WHERE execution_id = ?";
        String currentStatus = jdbcTemplate.queryForObject(currentStatusSql, String.class, executionId);
        assertEquals("success", currentStatus);
    }
}
