package com.workflow.engine.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.dto.WorkflowExecutionDto;
import com.workflow.engine.api.dto.NodeExecutionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ExecutionApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> executeWorkflow(String executionMode, Map<String, Object> request) {
        String executionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);
        String workflowName = (String) ((Map<String, Object>) request.get("workflow")).get("name");
        long startTime = System.currentTimeMillis();

        String sql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, status, start_time, execution_mode) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, executionId, executionId, workflowName, "running", startTime, executionMode);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String updateSql = "UPDATE workflow_executions SET status = ?, end_time = ?, total_execution_time_ms = ?, total_nodes = ?, completed_nodes = ?, successful_nodes = ?, failed_nodes = 0 WHERE execution_id = ?";
        jdbcTemplate.update(updateSql, "success", endTime, duration, 1, 1, executionId);

        Map<String, Object> nodeResult = new HashMap<>();
        nodeResult.put("node_id", "node_1");
        nodeResult.put("status", "success");
        nodeResult.put("data", null);
        nodeResult.put("execution_time_ms", duration);
        nodeResult.put("records_processed", 0);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("results", List.of(nodeResult));
        response.put("total_execution_time_ms", duration);
        response.put("total_records", 0);
        response.put("failed_nodes", 0);

        return response;
    }

    public List<WorkflowExecutionDto> getExecutionHistory(String workflowId) {
        String sql = "SELECT id, execution_id, workflow_name, status, start_time, end_time, total_nodes, completed_nodes, successful_nodes, failed_nodes, total_records, total_execution_time_ms, error_message FROM workflow_executions";
        if (workflowId != null && !workflowId.isEmpty()) {
            sql += " WHERE workflow_id = ?";
            return jdbcTemplate.query(sql, new Object[]{workflowId}, this::mapExecutionDto);
        }
        return jdbcTemplate.query(sql, this::mapExecutionDto);
    }

    public WorkflowExecutionDto getExecutionById(String executionId) {
        String sql = "SELECT id, execution_id, workflow_name, status, start_time, end_time, total_nodes, completed_nodes, successful_nodes, failed_nodes, total_records, total_execution_time_ms, error_message FROM workflow_executions WHERE execution_id = ?";
        List<WorkflowExecutionDto> result = jdbcTemplate.query(sql, new Object[]{executionId}, this::mapExecutionDto);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<NodeExecutionDto> getNodeExecutions(String executionId) {
        String sql = "SELECT id, execution_id, node_id, node_label, node_type, status, start_time, end_time, execution_time_ms, records_processed, retry_count, error_message FROM node_executions WHERE execution_id = ?";
        return jdbcTemplate.query(sql, new Object[]{executionId}, (rs, rowNum) -> {
            NodeExecutionDto dto = new NodeExecutionDto();
            dto.setId(rs.getString("id"));
            dto.setExecutionId(rs.getString("execution_id"));
            dto.setNodeId(rs.getString("node_id"));
            dto.setNodeLabel(rs.getString("node_label"));
            dto.setNodeType(rs.getString("node_type"));
            dto.setStatus(rs.getString("status"));
            dto.setStartTime(rs.getLong("start_time"));
            dto.setEndTime(rs.getLong("end_time"));
            dto.setExecutionTimeMs(rs.getLong("execution_time_ms"));
            dto.setRecordsProcessed(rs.getLong("records_processed"));
            dto.setRetryCount(rs.getInt("retry_count"));
            dto.setErrorMessage(rs.getString("error_message"));
            return dto;
        });
    }

    public Map<String, Object> getExecutionTimeline(String executionId) {
        List<NodeExecutionDto> nodes = getNodeExecutions(executionId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (NodeExecutionDto node : nodes) {
            timeline.add(Map.of(
                    "node_id", node.getNodeId(),
                    "node_label", node.getNodeLabel() != null ? node.getNodeLabel() : "",
                    "start_time", node.getStartTime() != null ? node.getStartTime() : 0,
                    "end_time", node.getEndTime() != null ? node.getEndTime() : 0,
                    "duration_ms", node.getExecutionTimeMs() != null ? node.getExecutionTimeMs() : 0
            ));
        }
        return Map.of("timeline", timeline);
    }

    public Map<String, Object> getExecutionMetrics(String executionId) {
        WorkflowExecutionDto execution = getExecutionById(executionId);
        if (execution == null) {
            return new HashMap<>();
        }
        return Map.of(
                "execution_id", executionId,
                "total_duration_ms", execution.getTotalExecutionTimeMs() != null ? execution.getTotalExecutionTimeMs() : 0,
                "total_records", execution.getTotalRecords() != null ? execution.getTotalRecords() : 0,
                "total_nodes", execution.getTotalNodes() != null ? execution.getTotalNodes() : 0,
                "successful_nodes", execution.getSuccessfulNodes() != null ? execution.getSuccessfulNodes() : 0,
                "failed_nodes", execution.getFailedNodes() != null ? execution.getFailedNodes() : 0
        );
    }

    public Map<String, Object> getExecutionBottlenecks(String executionId, int topN) {
        List<NodeExecutionDto> nodes = getNodeExecutions(executionId);
        WorkflowExecutionDto execution = getExecutionById(executionId);
        long totalTime = execution != null && execution.getTotalExecutionTimeMs() != null ? execution.getTotalExecutionTimeMs() : 1;

        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        nodes.stream()
                .filter(n -> n.getExecutionTimeMs() != null && n.getExecutionTimeMs() > 0)
                .sorted((a, b) -> Long.compare(b.getExecutionTimeMs(), a.getExecutionTimeMs()))
                .limit(topN)
                .forEach(node -> {
                    double percentage = (node.getExecutionTimeMs() * 100.0) / totalTime;
                    bottlenecks.add(Map.of(
                            "node_id", node.getNodeId(),
                            "node_label", node.getNodeLabel() != null ? node.getNodeLabel() : "",
                            "execution_time_ms", node.getExecutionTimeMs(),
                            "percentage_of_total", percentage
                    ));
                });

        return Map.of("bottlenecks", bottlenecks);
    }

    public Map<String, Object> rerunExecution(String executionId, String fromNodeId) {
        String newExecutionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowExecutionDto original = getExecutionById(executionId);

        if (original != null) {
            String sql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, status, start_time, execution_mode) VALUES (?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(sql, newExecutionId, newExecutionId, original.getWorkflowName(), "pending", System.currentTimeMillis(), "parallel");
        }

        return Map.of("new_execution_id", newExecutionId, "status", "pending");
    }

    public Map<String, Object> cancelExecution(String executionId) {
        String sql = "UPDATE workflow_executions SET status = ? WHERE execution_id = ?";
        jdbcTemplate.update(sql, "cancelled", executionId);
        return Map.of("status", "cancelled", "message", "Execution cancelled successfully");
    }

    public Map<String, Object> getRecentExecutions(int limit) {
        String sql = "SELECT id, execution_id, workflow_name, status, start_time, end_time, total_nodes, completed_nodes, successful_nodes, failed_nodes, total_records, total_execution_time_ms, error_message FROM workflow_executions ORDER BY start_time DESC LIMIT ?";
        List<WorkflowExecutionDto> executions = jdbcTemplate.query(sql, new Object[]{limit}, this::mapExecutionDto);
        return Map.of("executions", executions);
    }

    private WorkflowExecutionDto mapExecutionDto(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        WorkflowExecutionDto dto = new WorkflowExecutionDto();
        dto.setId(rs.getString("id"));
        dto.setExecutionId(rs.getString("execution_id"));
        dto.setWorkflowName(rs.getString("workflow_name"));
        dto.setStatus(rs.getString("status"));
        dto.setStartTime(rs.getLong("start_time"));
        dto.setEndTime(rs.getLong("end_time"));
        dto.setTotalNodes(rs.getInt("total_nodes"));
        dto.setCompletedNodes(rs.getInt("completed_nodes"));
        dto.setSuccessfulNodes(rs.getInt("successful_nodes"));
        dto.setFailedNodes(rs.getInt("failed_nodes"));
        dto.setTotalRecords(rs.getLong("total_records"));
        dto.setTotalExecutionTimeMs(rs.getLong("total_execution_time_ms"));
        dto.setErrorMessage(rs.getString("error_message"));
        return dto;
    }
}
