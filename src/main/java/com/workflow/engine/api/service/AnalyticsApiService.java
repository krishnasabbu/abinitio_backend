package com.workflow.engine.api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getGlobalAnalytics() {
        String totalExecutions = "SELECT COUNT(*) FROM workflow_executions";
        String successfulExecutions = "SELECT COUNT(*) FROM workflow_executions WHERE status = 'success'";
        String failedExecutions = "SELECT COUNT(*) FROM workflow_executions WHERE status = 'failed'";
        String avgDuration = "SELECT AVG(total_execution_time_ms) FROM workflow_executions WHERE total_execution_time_ms > 0";

        Integer total = jdbcTemplate.queryForObject(totalExecutions, Integer.class);
        Integer successful = jdbcTemplate.queryForObject(successfulExecutions, Integer.class);
        Integer failed = jdbcTemplate.queryForObject(failedExecutions, Integer.class);
        Double avgDur = jdbcTemplate.queryForObject(avgDuration, Double.class);

        return Map.of(
                "total_executions", total != null ? total : 0,
                "successful_executions", successful != null ? successful : 0,
                "failed_executions", failed != null ? failed : 0,
                "avg_duration_ms", avgDur != null ? avgDur : 0.0,
                "success_rate", total != null && total > 0 ? (successful * 100.0 / total) : 0.0
        );
    }

    public Map<String, Object> getAnalyticsTrends(int days) {
        List<Map<String, Object>> trends = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            long timestamp = System.currentTimeMillis() - (i * 86400000L);
            trends.add(Map.of(
                    "date", new Date(timestamp),
                    "executions", (int) (Math.random() * 100) + 20,
                    "success_rate", 0.85 + Math.random() * 0.14
            ));
        }
        return Map.of("trends", trends, "period_days", days);
    }

    public Map<String, Object> getNodeTypeStats() {
        String sql = "SELECT node_type, COUNT(*) as count, " +
                "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as successful, " +
                "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed, " +
                "AVG(execution_time_ms) as avg_duration " +
                "FROM node_executions WHERE node_type IS NOT NULL GROUP BY node_type";

        List<Map<String, Object>> stats = jdbcTemplate.queryForList(sql);
        return Map.of("node_types", stats);
    }

    public Map<String, Object> getExecutionHealth(String executionId) {
        String sql = "SELECT status, total_nodes, successful_nodes, failed_nodes, " +
                "total_execution_time_ms FROM workflow_executions WHERE execution_id = ?";

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, executionId);
        if (result.isEmpty()) {
            return Map.of("status", "not_found");
        }

        Map<String, Object> exec = result.get(0);
        Integer totalNodes = ((Number) exec.getOrDefault("total_nodes", 0)).intValue();
        Integer successfulNodes = ((Number) exec.getOrDefault("successful_nodes", 0)).intValue();
        Integer failedNodes = ((Number) exec.getOrDefault("failed_nodes", 0)).intValue();

        double healthScore = totalNodes > 0 ? (successfulNodes * 100.0 / totalNodes) : 0;

        return Map.of(
                "execution_id", executionId,
                "status", exec.get("status"),
                "health_score", healthScore,
                "total_nodes", totalNodes,
                "successful_nodes", successfulNodes,
                "failed_nodes", failedNodes
        );
    }

    public Map<String, Object> getExecutionPerformance(String executionId) {
        String sql = "SELECT total_execution_time_ms, total_records, total_nodes FROM workflow_executions WHERE execution_id = ?";
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, executionId);

        if (result.isEmpty()) {
            return Map.of("status", "not_found");
        }

        Map<String, Object> exec = result.get(0);
        Long duration = ((Number) exec.getOrDefault("total_execution_time_ms", 0)).longValue();
        Long records = ((Number) exec.getOrDefault("total_records", 0)).longValue();

        double throughput = duration > 0 ? (records * 1000.0 / duration) : 0;

        return Map.of(
                "execution_id", executionId,
                "total_duration_ms", duration,
                "total_records_processed", records,
                "throughput_records_per_sec", throughput,
                "total_nodes", exec.get("total_nodes")
        );
    }

    public Map<String, Object> getSystemOverviewAnalytics() {
        String totalWf = "SELECT COUNT(*) FROM workflows";
        String totalExec = "SELECT COUNT(*) FROM workflow_executions";
        String totalLogs = "SELECT COUNT(*) FROM execution_logs";
        String avgDuration = "SELECT AVG(total_execution_time_ms) FROM workflow_executions WHERE total_execution_time_ms > 0";

        Integer wfCount = jdbcTemplate.queryForObject(totalWf, Integer.class);
        Integer execCount = jdbcTemplate.queryForObject(totalExec, Integer.class);
        Integer logCount = jdbcTemplate.queryForObject(totalLogs, Integer.class);
        Double avgDur = jdbcTemplate.queryForObject(avgDuration, Double.class);

        return Map.of(
                "total_workflows", wfCount != null ? wfCount : 0,
                "total_executions", execCount != null ? execCount : 0,
                "total_logs", logCount != null ? logCount : 0,
                "avg_execution_duration_ms", avgDur != null ? avgDur : 0.0
        );
    }
}
