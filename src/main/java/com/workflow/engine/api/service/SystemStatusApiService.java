package com.workflow.engine.api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SystemStatusApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getSystemStatus() {
        String sql = "SELECT engine_status, scheduler_status, timestamp, last_heartbeat, total_workflows, running_executions, supported_modes FROM system_status WHERE id = 1";
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);

        String totalWorkflowsSql = "SELECT COUNT(*) FROM workflows";
        String runningExecutionsSql = "SELECT COUNT(*) FROM workflow_executions WHERE status = 'running'";

        Integer totalWorkflows = jdbcTemplate.queryForObject(totalWorkflowsSql, Integer.class);
        Integer runningExecutions = jdbcTemplate.queryForObject(runningExecutionsSql, Integer.class);

        if (result.isEmpty()) {
            return Map.of(
                    "engine_status", "running",
                    "scheduler_status", "active",
                    "timestamp", System.currentTimeMillis(),
                    "last_heartbeat", System.currentTimeMillis(),
                    "total_workflows", totalWorkflows != null ? totalWorkflows : 0,
                    "running_executions", runningExecutions != null ? runningExecutions : 0,
                    "supported_modes", List.of("python", "parallel", "pyspark")
            );
        }

        Map<String, Object> status = result.get(0);
        String modesStr = (String) status.get("supported_modes");
        List<String> modes = modesStr != null ? Arrays.asList(modesStr.split(",")) : List.of("python", "parallel", "pyspark");

        return Map.of(
                "engine_status", status.get("engine_status"),
                "scheduler_status", status.get("scheduler_status"),
                "timestamp", status.get("timestamp"),
                "last_heartbeat", status.get("last_heartbeat"),
                "total_workflows", totalWorkflows != null ? totalWorkflows : 0,
                "running_executions", runningExecutions != null ? runningExecutions : 0,
                "supported_modes", modes
        );
    }
}
