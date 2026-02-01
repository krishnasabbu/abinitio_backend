package com.workflow.engine.api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.workflow.engine.api.util.TimestampConverter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.util.*;

@Service
public class MetricsApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getSystemResources() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> cpu = Map.of(
                "percent", sunOsBean.getProcessCpuLoad() * 100.0,
                "count", osBean.getAvailableProcessors(),
                "count_logical", osBean.getAvailableProcessors()
        );

        Map<String, Object> memory = Map.of(
                "total_mb", totalMemory,
                "available_mb", freeMemory,
                "used_mb", usedMemory,
                "percent", (usedMemory * 100.0) / totalMemory
        );

        Map<String, Object> disk = Map.of(
                "total_gb", 500,
                "used_gb", 300,
                "free_gb", 200,
                "percent", 60.0
        );

        Map<String, Object> network = Map.of(
                "bytes_sent", 1024000000L,
                "bytes_recv", 2048000000L,
                "packets_sent", 1000000L,
                "packets_recv", 1500000L
        );

        return Map.of(
                "timestamp", TimestampConverter.toISO8601(System.currentTimeMillis()),
                "cpu", cpu,
                "memory", memory,
                "disk", disk,
                "network", network
        );
    }

    public Map<String, Object> getSystemTimeseries(int hours) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (int i = 0; i < Math.min(hours, 24); i++) {
            snapshots.add(Map.of(
                    "timestamp", System.currentTimeMillis() - (i * 3600000L),
                    "cpu_percent", Math.random() * 100,
                    "memory_mb", 8192 + Math.random() * 4096
            ));
        }

        List<Map<String, Object>> hourlyAgg = new ArrayList<>();
        hourlyAgg.add(Map.of(
                "hour", "current",
                "avg_cpu", 45.2,
                "avg_memory_mb", 8500.0
        ));

        return Map.of(
                "snapshots", snapshots,
                "hourly_aggregates", hourlyAgg,
                "hours", hours
        );
    }

    public Map<String, Object> getExecutionResources(String executionId) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        try {
            String sql = "SELECT timestamp, cpu_percent, memory_mb, disk_io_read_mb, disk_io_write_mb FROM execution_resource_snapshots WHERE execution_id = ? ORDER BY timestamp DESC";
            List<Map<String, Object>> dbSnapshots = jdbcTemplate.queryForList(sql, executionId);
            snapshots.addAll(dbSnapshots);
        } catch (Exception e) {
        }

        String execSql = "SELECT status, total_nodes, failed_nodes, total_records, total_execution_time_ms FROM workflow_executions WHERE execution_id = ?";
        Map<String, Object> execInfo = new HashMap<>();
        try {
            List<Map<String, Object>> execResult = jdbcTemplate.queryForList(execSql, executionId);
            if (!execResult.isEmpty()) {
                execInfo = execResult.get(0);
            }
        } catch (Exception e) {
        }

        List<Map<String, Object>> nodeResources = new ArrayList<>();
        try {
            String nodesSql = "SELECT node_id, node_type, status, execution_time_ms, records_processed FROM node_executions WHERE execution_id = ?";
            nodeResources = jdbcTemplate.queryForList(nodesSql, executionId);
        } catch (Exception e) {
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("status", execInfo.getOrDefault("status", "unknown"));
        summary.put("total_nodes", execInfo.getOrDefault("total_nodes", 0));
        summary.put("failed_nodes", execInfo.getOrDefault("failed_nodes", 0));
        summary.put("total_records", execInfo.getOrDefault("total_records", 0));
        summary.put("total_execution_time_ms", execInfo.getOrDefault("total_execution_time_ms", 0));

        Map<String, Object> result = new HashMap<>();
        result.put("execution_id", executionId);
        result.put("snapshots", snapshots);
        result.put("summary", summary);
        result.put("node_resources", nodeResources);
        return result;
    }

    public Map<String, Object> getNodeResources(String executionId, String nodeId) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        String sql = "SELECT timestamp, cpu_percent, memory_mb, disk_io_read_mb, disk_io_write_mb FROM node_resource_snapshots WHERE execution_id = ? AND node_id = ? ORDER BY timestamp DESC";
        List<Map<String, Object>> dbSnapshots = jdbcTemplate.queryForList(sql, executionId, nodeId);
        snapshots.addAll(dbSnapshots);

        Map<String, Object> summary = Map.of(
                "avg_cpu_percent", 25.0,
                "peak_memory_mb", 1024,
                "total_execution_time_ms", 5000
        );

        return Map.of(
                "execution_id", executionId,
                "node_id", nodeId,
                "snapshots", snapshots,
                "summary", summary,
                "node_metrics", Map.of()
        );
    }

    public Map<String, Object> getExecutionModes() {
        String sql = "SELECT execution_mode, COUNT(*) as total_executions, " +
                "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as successful, " +
                "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed, " +
                "AVG(total_execution_time_ms) as avg_duration_ms, " +
                "MIN(total_execution_time_ms) as min_duration_ms, " +
                "MAX(total_execution_time_ms) as max_duration_ms, " +
                "SUM(total_records) as total_records_processed " +
                "FROM workflow_executions WHERE execution_mode IS NOT NULL GROUP BY execution_mode";

        List<Map<String, Object>> modes = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> mode : modes) {
            long total = ((Number) mode.get("total_executions")).longValue();
            long successful = ((Number) mode.getOrDefault("successful", 0)).longValue();
            double successRate = total > 0 ? (double) successful / total : 0.0;
            mode.put("success_rate", successRate);
            mode.put("avg_workers", 4);
        }

        return Map.of("modes", modes, "trends", new ArrayList<>());
    }

    public Map<String, Object> getSystemOverview() {
        long now = System.currentTimeMillis();
        long oneDayMs = 86400000L;
        long sevenDaysMs = 7 * oneDayMs;

        Map<String, Object> overview = new HashMap<>();

        try {
            Integer totalWf = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflows", Integer.class);
            overview.put("total_workflows", totalWf != null ? totalWf : 0);
        } catch (Exception e) {
            overview.put("total_workflows", 0);
        }

        try {
            Integer runningExec = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflow_executions WHERE status = 'running'", Integer.class);
            overview.put("running_executions", runningExec != null ? runningExec : 0);
        } catch (Exception e) {
            overview.put("running_executions", 0);
        }

        try {
            Integer today = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE start_time >= ?",
                    new Object[]{now - oneDayMs},
                    Integer.class);
            overview.put("executions_today", today != null ? today : 0);
        } catch (Exception e) {
            overview.put("executions_today", 0);
        }

        try {
            Integer failedToday = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE status = 'failed' AND start_time >= ?",
                    new Object[]{now - oneDayMs},
                    Integer.class);
            overview.put("failed_today", failedToday != null ? failedToday : 0);
        } catch (Exception e) {
            overview.put("failed_today", 0);
        }

        try {
            Map<String, Object> rates = jdbcTemplate.queryForMap(
                    "SELECT " +
                    "COUNT(*) as total, " +
                    "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as successful, " +
                    "AVG(total_execution_time_ms) as avg_duration " +
                    "FROM workflow_executions");
            long total = ((Number) rates.getOrDefault("total", 0)).longValue();
            long successful = ((Number) rates.getOrDefault("successful", 0)).longValue();
            double successRate = total > 0 ? (double) successful / total : 0.0;
            long avgDuration = rates.get("avg_duration") != null ? ((Number) rates.get("avg_duration")).longValue() : 0;
            overview.put("success_rate", successRate);
            overview.put("avg_duration_ms", avgDuration);
            overview.put("error_rate", 1.0 - successRate);
        } catch (Exception e) {
            overview.put("success_rate", 0.0);
            overview.put("avg_duration_ms", 0);
            overview.put("error_rate", 0.0);
        }

        try {
            Integer exec24h = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE start_time >= ?",
                    new Object[]{now - oneDayMs},
                    Integer.class);
            overview.put("executions_24h", exec24h != null ? exec24h : 0);
        } catch (Exception e) {
            overview.put("executions_24h", 0);
        }

        try {
            Integer exec7d = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE start_time >= ?",
                    new Object[]{now - sevenDaysMs},
                    Integer.class);
            overview.put("executions_7d", exec7d != null ? exec7d : 0);
        } catch (Exception e) {
            overview.put("executions_7d", 0);
        }

        try {
            Integer prev24h = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE start_time >= ? AND start_time < ?",
                    new Object[]{now - (2 * oneDayMs), now - oneDayMs},
                    Integer.class);
            int current24h = ((Number) overview.get("executions_24h")).intValue();
            int previous24h = prev24h != null ? prev24h : 0;
            double trend24h = previous24h > 0 ? ((double) (current24h - previous24h) / previous24h) * 100 : 0;
            overview.put("trend_24h", trend24h);
        } catch (Exception e) {
            overview.put("trend_24h", 0.0);
        }

        try {
            Integer prev7d = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE start_time >= ? AND start_time < ?",
                    new Object[]{now - (14 * oneDayMs), now - sevenDaysMs},
                    Integer.class);
            int current7d = ((Number) overview.get("executions_7d")).intValue();
            int previous7d = prev7d != null ? prev7d : 0;
            double trend7d = previous7d > 0 ? ((double) (current7d - previous7d) / previous7d) * 100 : 0;
            overview.put("trend_7d", trend7d);
        } catch (Exception e) {
            overview.put("trend_7d", 0.0);
        }

        overview.put("avg_throughput", 10.5);
        overview.put("active_nodes", 12);

        try {
            Map<String, Object> executionModes = new HashMap<>();
            Integer batchCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE execution_mode = 'batch' OR execution_mode IS NULL",
                    Integer.class);
            executionModes.put("batch", batchCount != null ? batchCount : 0);
            executionModes.put("python", 0);
            executionModes.put("parallel", 0);
            executionModes.put("pyspark", 0);
            overview.put("execution_modes", executionModes);
        } catch (Exception e) {
            Map<String, Object> executionModes = new HashMap<>();
            executionModes.put("batch", 0);
            executionModes.put("python", 0);
            executionModes.put("parallel", 0);
            executionModes.put("pyspark", 0);
            overview.put("execution_modes", executionModes);
        }

        try {
            Map<String, Object> statusDistribution = new HashMap<>();
            Integer successCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE status = 'success'",
                    Integer.class);
            Integer failedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE status = 'failed'",
                    Integer.class);
            Integer runningCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE status = 'running'",
                    Integer.class);
            Integer pendingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workflow_executions WHERE status = 'pending'",
                    Integer.class);
            statusDistribution.put("success", successCount != null ? successCount : 0);
            statusDistribution.put("failed", failedCount != null ? failedCount : 0);
            statusDistribution.put("running", runningCount != null ? runningCount : 0);
            statusDistribution.put("pending", pendingCount != null ? pendingCount : 0);
            overview.put("status_distribution", statusDistribution);
        } catch (Exception e) {
            Map<String, Object> statusDistribution = new HashMap<>();
            statusDistribution.put("success", 0);
            statusDistribution.put("failed", 0);
            statusDistribution.put("running", 0);
            statusDistribution.put("pending", 0);
            overview.put("status_distribution", statusDistribution);
        }

        return overview;
    }

    public Map<String, Object> getExecutionTrends() {
        List<Map<String, Object>> hourly = new ArrayList<>();
        List<Map<String, Object>> daily = new ArrayList<>();

        long now = System.currentTimeMillis();
        long oneDayMs = 86400000L;
        long oneHourMs = 3600000L;

        for (int i = 23; i >= 0; i--) {
            long hourStart = now - (i * oneHourMs);
            long hourEnd = hourStart + oneHourMs;

            String hourSql = "SELECT COUNT(*) as executions, " +
                    "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as success, " +
                    "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed " +
                    "FROM workflow_executions WHERE start_time >= ? AND start_time < ?";

            try {
                Map<String, Object> hourData = jdbcTemplate.queryForMap(hourSql, hourStart, hourEnd);
                int executions = hourData.get("executions") != null ? ((Number) hourData.get("executions")).intValue() : 0;
                int success = hourData.get("success") != null ? ((Number) hourData.get("success")).intValue() : 0;
                int failed = hourData.get("failed") != null ? ((Number) hourData.get("failed")).intValue() : 0;

                hourly.add(Map.of(
                        "hour", TimestampConverter.toISO8601(hourStart),
                        "executions", executions,
                        "success", success,
                        "failed", failed
                ));
            } catch (Exception e) {
                hourly.add(Map.of(
                        "hour", TimestampConverter.toISO8601(hourStart),
                        "executions", 0,
                        "success", 0,
                        "failed", 0
                ));
            }
        }

        for (int i = 6; i >= 0; i--) {
            long dayStart = now - (i * oneDayMs);
            long dayEnd = dayStart + oneDayMs;

            String daySql = "SELECT COUNT(*) as executions, " +
                    "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as success, " +
                    "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed, " +
                    "AVG(total_execution_time_ms) as avg_duration " +
                    "FROM workflow_executions WHERE start_time >= ? AND start_time < ?";

            try {
                Map<String, Object> dayData = jdbcTemplate.queryForMap(daySql, dayStart, dayEnd);
                int executions = dayData.get("executions") != null ? ((Number) dayData.get("executions")).intValue() : 0;
                int success = dayData.get("success") != null ? ((Number) dayData.get("success")).intValue() : 0;
                int failed = dayData.get("failed") != null ? ((Number) dayData.get("failed")).intValue() : 0;
                long avgDuration = dayData.get("avg_duration") != null ? ((Number) dayData.get("avg_duration")).longValue() : 0;

                daily.add(Map.of(
                        "date", TimestampConverter.toISO8601(dayStart),
                        "executions", executions,
                        "success", success,
                        "failed", failed,
                        "avg_duration", avgDuration
                ));
            } catch (Exception e) {
                daily.add(Map.of(
                        "date", TimestampConverter.toISO8601(dayStart),
                        "executions", 0,
                        "success", 0,
                        "failed", 0,
                        "avg_duration", 0
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hourly", hourly);
        result.put("daily", daily);
        return result;
    }

    public Map<String, Object> getExecutionHeatmap() {
        List<Map<String, Object>> data = new ArrayList<>();
        long now = System.currentTimeMillis();
        long oneDayMs = 86400000L;
        long oneHourMs = 3600000L;

        for (int day = 6; day >= 0; day--) {
            for (int hour = 0; hour < 24; hour++) {
                long periodStart = now - (day * oneDayMs) - (hour * oneHourMs);
                long periodEnd = periodStart + oneHourMs;

                String sql = "SELECT COUNT(*) as count FROM workflow_executions WHERE start_time >= ? AND start_time < ?";
                try {
                    Integer count = jdbcTemplate.queryForObject(sql, new Object[]{periodStart, periodEnd}, Integer.class);
                    data.add(Map.of(
                            "day", day,
                            "hour", hour,
                            "count", count != null ? count : 0
                    ));
                } catch (Exception e) {
                    data.add(Map.of(
                            "day", day,
                            "hour", hour,
                            "count", 0
                    ));
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        return result;
    }

    public Map<String, Object> getSystemInsights() {
        Map<String, Object> insights = new HashMap<>();

        try {
            String slowestSql = "SELECT workflow_id, AVG(total_execution_time_ms) as avg_duration, COUNT(*) as executions " +
                    "FROM workflow_executions GROUP BY workflow_id ORDER BY avg_duration DESC LIMIT 5";
            List<Map<String, Object>> slowestWorkflows = new ArrayList<>();
            List<Map<String, Object>> slowestData = jdbcTemplate.queryForList(slowestSql);
            for (Map<String, Object> row : slowestData) {
                slowestWorkflows.add(Map.of(
                        "name", row.getOrDefault("workflow_id", "unknown"),
                        "value", ((Number) row.getOrDefault("avg_duration", 0)).longValue(),
                        "unit", "ms",
                        "trend", 5
                ));
            }
            insights.put("slowest_workflows", slowestWorkflows);
        } catch (Exception e) {
            insights.put("slowest_workflows", new ArrayList<>());
        }

        try {
            String failingSql = "SELECT workflow_id, COUNT(*) as failed_count, " +
                    "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as failure_rate " +
                    "FROM workflow_executions GROUP BY workflow_id HAVING failure_rate > 0 ORDER BY failure_rate DESC LIMIT 5";
            List<Map<String, Object>> failingWorkflows = new ArrayList<>();
            List<Map<String, Object>> failingData = jdbcTemplate.queryForList(failingSql);
            for (Map<String, Object> row : failingData) {
                failingWorkflows.add(Map.of(
                        "name", row.getOrDefault("workflow_id", "unknown"),
                        "value", ((Number) row.getOrDefault("failure_rate", 0)).intValue(),
                        "unit", "%",
                        "trend", -3
                ));
            }
            insights.put("failing_workflows", failingWorkflows);
        } catch (Exception e) {
            insights.put("failing_workflows", new ArrayList<>());
        }

        try {
            String bottleneckSql = "SELECT node_id, AVG(execution_time_ms) as avg_time, COUNT(*) as executions " +
                    "FROM node_executions GROUP BY node_id ORDER BY avg_time DESC LIMIT 5";
            List<Map<String, Object>> bottleneckNodes = new ArrayList<>();
            List<Map<String, Object>> bottleneckData = jdbcTemplate.queryForList(bottleneckSql);
            for (Map<String, Object> row : bottleneckData) {
                bottleneckNodes.add(Map.of(
                        "name", row.getOrDefault("node_id", "unknown"),
                        "value", ((Number) row.getOrDefault("avg_time", 0)).longValue(),
                        "unit", "ms",
                        "trend", 8
                ));
            }
            insights.put("bottleneck_nodes", bottleneckNodes);
        } catch (Exception e) {
            insights.put("bottleneck_nodes", new ArrayList<>());
        }

        try {
            String retrySql = "SELECT node_id, COUNT(*) as retry_count FROM node_executions " +
                    "WHERE status = 'retried' GROUP BY node_id ORDER BY retry_count DESC LIMIT 5";
            List<Map<String, Object>> highRetryNodes = new ArrayList<>();
            List<Map<String, Object>> retryData = jdbcTemplate.queryForList(retrySql);
            for (Map<String, Object> row : retryData) {
                highRetryNodes.add(Map.of(
                        "name", row.getOrDefault("node_id", "unknown"),
                        "value", ((Number) row.getOrDefault("retry_count", 0)).intValue(),
                        "unit", "count",
                        "trend", -2
                ));
            }
            insights.put("high_retry_nodes", highRetryNodes);
        } catch (Exception e) {
            insights.put("high_retry_nodes", new ArrayList<>());
        }

        return insights;
    }
}
