package com.workflow.engine.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.dto.WorkflowDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    public List<WorkflowDto> getAllWorkflows() {
        String sql = "SELECT id, name, description, workflow_data, created_at, updated_at FROM workflows";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            WorkflowDto dto = new WorkflowDto();
            dto.setId(rs.getString("id"));
            dto.setName(rs.getString("name"));
            dto.setDescription(rs.getString("description"));
            try {
                dto.setWorkflowData(objectMapper.readValue(rs.getString("workflow_data"), Map.class));
            } catch (Exception e) {
                dto.setWorkflowData(new HashMap<>());
            }
            dto.setCreatedAt(rs.getString("created_at"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            return dto;
        });
    }

    public WorkflowDto getWorkflowById(String id) {
        String sql = "SELECT id, name, description, workflow_data, created_at, updated_at FROM workflows WHERE id = ?";
        List<WorkflowDto> result = jdbcTemplate.query(sql, new Object[]{id}, (rs, rowNum) -> {
            WorkflowDto dto = new WorkflowDto();
            dto.setId(rs.getString("id"));
            dto.setName(rs.getString("name"));
            dto.setDescription(rs.getString("description"));
            try {
                dto.setWorkflowData(objectMapper.readValue(rs.getString("workflow_data"), Map.class));
            } catch (Exception e) {
                dto.setWorkflowData(new HashMap<>());
            }
            dto.setCreatedAt(rs.getString("created_at"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            return dto;
        });
        return result.isEmpty() ? null : result.get(0);
    }

    public String createWorkflow(WorkflowDto request) {
        String id = "wf_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();
        String workflowDataJson = toJson(request.getWorkflowData());

        String sql = "INSERT INTO workflows (id, name, description, workflow_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, request.getName(), request.getDescription(), workflowDataJson, now, now);
        return id;
    }

    public void updateWorkflow(String id, WorkflowDto request) {
        String now = Instant.now().toString();
        String workflowDataJson = toJson(request.getWorkflowData());

        String sql = "UPDATE workflows SET name = ?, description = ?, workflow_data = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, request.getName(), request.getDescription(), workflowDataJson, now, id);
    }

    public void deleteWorkflow(String id) {
        String sql = "DELETE FROM workflows WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public Map<String, Object> getWorkflowStats() {
        String sql = "SELECT w.name AS workflow_name, " +
                "COUNT(we.id) AS total_executions, " +
                "SUM(CASE WHEN we.status = 'success' THEN 1 ELSE 0 END) AS successful_executions, " +
                "SUM(CASE WHEN we.status = 'failed' THEN 1 ELSE 0 END) AS failed_executions, " +
                "AVG(we.total_execution_time_ms) AS avg_duration_ms, " +
                "AVG(we.total_records) AS avg_records, " +
                "MAX(we.status) AS last_execution_status, " +
                "MAX(we.end_time) AS last_execution_time, " +
                "MAX(we.execution_mode) AS execution_mode " +
                "FROM workflows w LEFT JOIN workflow_executions we ON w.id = we.workflow_id " +
                "GROUP BY w.id, w.name";

        List<Map<String, Object>> stats = jdbcTemplate.queryForList(sql);
        return Map.of("workflows", stats);
    }

    public Map<String, Object> getRecentWorkflows(int limit) {
        String sql = "SELECT id, name, description, workflow_data, created_at, updated_at FROM workflows ORDER BY updated_at DESC LIMIT ?";
        List<WorkflowDto> workflows = jdbcTemplate.query(sql, new Object[]{limit}, (rs, rowNum) -> {
            WorkflowDto dto = new WorkflowDto();
            dto.setId(rs.getString("id"));
            dto.setName(rs.getString("name"));
            dto.setDescription(rs.getString("description"));
            try {
                dto.setWorkflowData(objectMapper.readValue(rs.getString("workflow_data"), Map.class));
            } catch (Exception e) {
                dto.setWorkflowData(new HashMap<>());
            }
            dto.setCreatedAt(rs.getString("created_at"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            return dto;
        });
        return Map.of("workflows", workflows);
    }

    public Map<String, Object> getWorkflowAnalytics(String workflowId) {
        String sql = "SELECT COUNT(*) AS total_executions, " +
                "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS successful, " +
                "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed, " +
                "AVG(total_execution_time_ms) AS avg_duration, " +
                "MAX(total_execution_time_ms) AS max_duration, " +
                "MIN(total_execution_time_ms) AS min_duration " +
                "FROM workflow_executions WHERE workflow_id = ?";

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, workflowId);
        return result.isEmpty() ? new HashMap<>() : result.get(0);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
