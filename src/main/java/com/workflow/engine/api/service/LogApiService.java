package com.workflow.engine.api.service;

import com.workflow.engine.api.dto.LogEntryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LogApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> listExecutionLogs() {
        String sql = "SELECT DISTINCT execution_id, COUNT(*) as count FROM execution_logs GROUP BY execution_id";
        List<Map<String, Object>> logs = new ArrayList<>();

        String querySql = "SELECT execution_id FROM execution_logs GROUP BY execution_id";
        List<String> executionIds = jdbcTemplate.queryForList(querySql, String.class);

        for (String execId : executionIds) {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("execution_id", execId);
            logEntry.put("file_path", "/logs/" + execId + ".log");
            logEntry.put("file_size_bytes", 1024000);
            logEntry.put("created_at", System.currentTimeMillis());
            logEntry.put("modified_at", System.currentTimeMillis());
            logs.add(logEntry);
        }

        return Map.of("logs", logs, "total", logs.size());
    }

    public Map<String, Object> getExecutionLogs(String executionId, String level, String nodeId, String search, Long startTime, Long endTime, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT timestamp, datetime, level, execution_id, workflow_id, node_id, message, metadata, stack_trace FROM execution_logs WHERE execution_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(executionId);

        if (level != null && !level.isEmpty()) {
            sql.append(" AND level = ?");
            params.add(level);
        }
        if (nodeId != null && !nodeId.isEmpty()) {
            sql.append(" AND node_id = ?");
            params.add(nodeId);
        }
        if (search != null && !search.isEmpty()) {
            sql.append(" AND message LIKE ?");
            params.add("%" + search + "%");
        }
        if (startTime != null) {
            sql.append(" AND timestamp >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND timestamp <= ?");
            params.add(endTime);
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<LogEntryDto> logs = jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            LogEntryDto dto = new LogEntryDto();
            dto.setTimestamp(rs.getLong("timestamp"));
            dto.setDatetime(rs.getString("datetime"));
            dto.setLevel(rs.getString("level"));
            dto.setExecutionId(rs.getString("execution_id"));
            dto.setWorkflowId(rs.getString("workflow_id"));
            dto.setNodeId(rs.getString("node_id"));
            dto.setMessage(rs.getString("message"));
            dto.setStackTrace(rs.getString("stack_trace"));
            return dto;
        });

        String countSql = "SELECT COUNT(*) FROM execution_logs WHERE execution_id = ?";
        int total = jdbcTemplate.queryForObject(countSql, Integer.class, executionId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("levels", getLevelCounts(executionId));
        summary.put("nodes", getNodesList(executionId));
        summary.put("first_timestamp", getFirstTimestamp(executionId));
        summary.put("last_timestamp", getLastTimestamp(executionId));

        return Map.of(
                "execution_id", executionId,
                "logs", logs,
                "total", logs.size(),
                "summary", summary
        );
    }

    public Map<String, Object> getNodeLogs(String executionId, String nodeId, String level, int limit) {
        StringBuilder sql = new StringBuilder("SELECT timestamp, datetime, level, execution_id, workflow_id, node_id, message, metadata, stack_trace FROM execution_logs WHERE execution_id = ? AND node_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(executionId);
        params.add(nodeId);

        if (level != null && !level.isEmpty()) {
            sql.append(" AND level = ?");
            params.add(level);
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ?");
        params.add(limit);

        List<LogEntryDto> logs = jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            LogEntryDto dto = new LogEntryDto();
            dto.setTimestamp(rs.getLong("timestamp"));
            dto.setDatetime(rs.getString("datetime"));
            dto.setLevel(rs.getString("level"));
            dto.setExecutionId(rs.getString("execution_id"));
            dto.setWorkflowId(rs.getString("workflow_id"));
            dto.setNodeId(rs.getString("node_id"));
            dto.setMessage(rs.getString("message"));
            return dto;
        });

        return Map.of(
                "execution_id", executionId,
                "node_id", nodeId,
                "logs", logs,
                "total", logs.size()
        );
    }

    public Map<String, Object> searchLogs(String search, String level, String executionId, Long startTime, Long endTime, int limit) {
        StringBuilder sql = new StringBuilder("SELECT timestamp, datetime, level, execution_id, workflow_id, node_id, message, metadata, stack_trace FROM execution_logs WHERE message LIKE ?");
        List<Object> params = new ArrayList<>();
        params.add("%" + search + "%");

        if (level != null && !level.isEmpty()) {
            sql.append(" AND level = ?");
            params.add(level);
        }
        if (executionId != null && !executionId.isEmpty()) {
            sql.append(" AND execution_id = ?");
            params.add(executionId);
        }
        if (startTime != null) {
            sql.append(" AND timestamp >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            sql.append(" AND timestamp <= ?");
            params.add(endTime);
        }

        sql.append(" ORDER BY timestamp DESC LIMIT ?");
        params.add(limit);

        List<LogEntryDto> results = jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            LogEntryDto dto = new LogEntryDto();
            dto.setTimestamp(rs.getLong("timestamp"));
            dto.setDatetime(rs.getString("datetime"));
            dto.setLevel(rs.getString("level"));
            dto.setExecutionId(rs.getString("execution_id"));
            dto.setMessage(rs.getString("message"));
            return dto;
        });

        return Map.of(
                "query", search,
                "results", results,
                "total", results.size()
        );
    }

    public Map<String, Object> getLogSummary(String executionId) {
        String sql = "SELECT COUNT(*) as total, level FROM execution_logs WHERE execution_id = ? GROUP BY level";
        Map<String, Integer> levels = new HashMap<>();

        // Initialize all levels with 0
        levels.put("INFO", 0);
        levels.put("ERROR", 0);
        levels.put("WARNING", 0);
        levels.put("DEBUG", 0);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, executionId);
        int total = 0;
        for (Map<String, Object> row : results) {
            String lvl = (String) row.get("level");
            Integer cnt = ((Number) row.get("total")).intValue();
            levels.put(lvl, cnt);
            total += cnt;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("levels", levels);

        List<String> nodes = getNodesList(executionId);
        if (nodes != null && !nodes.isEmpty()) {
            result.put("nodes", nodes);
        }

        Long firstTimestamp = getFirstTimestamp(executionId);
        if (firstTimestamp != null) {
            result.put("first_timestamp", firstTimestamp);
        }

        Long lastTimestamp = getLastTimestamp(executionId);
        if (lastTimestamp != null) {
            result.put("last_timestamp", lastTimestamp);
        }

        return result;
    }

    public Map<String, Object> getAnalyticsExecutionLogs(String executionId, int limit) {
        String sql = "SELECT timestamp, datetime, level, execution_id, workflow_id, node_id, message, metadata, stack_trace FROM execution_logs WHERE execution_id = ? ORDER BY timestamp DESC LIMIT ?";
        List<LogEntryDto> logs = jdbcTemplate.query(sql, new Object[]{executionId, limit}, (rs, rowNum) -> {
            LogEntryDto dto = new LogEntryDto();
            dto.setTimestamp(rs.getLong("timestamp"));
            dto.setDatetime(rs.getString("datetime"));
            dto.setLevel(rs.getString("level"));
            dto.setExecutionId(rs.getString("execution_id"));
            dto.setMessage(rs.getString("message"));
            return dto;
        });

        return Map.of("logs", logs);
    }

    public Map<String, Object> getLogAnalysis(String executionId) {
        Map<String, Integer> levels = new HashMap<>();
        String levelSql = "SELECT level, COUNT(*) as count FROM execution_logs WHERE execution_id = ? GROUP BY level";
        List<Map<String, Object>> levelResults = jdbcTemplate.queryForList(levelSql, executionId);
        for (Map<String, Object> row : levelResults) {
            levels.put((String) row.get("level"), ((Number) row.get("count")).intValue());
        }

        return Map.of(
                "execution_id", executionId,
                "total_logs", levels.values().stream().mapToInt(Integer::intValue).sum(),
                "level_distribution", levels,
                "error_count", levels.getOrDefault("ERROR", 0),
                "warning_count", levels.getOrDefault("WARN", 0)
        );
    }

    private Map<String, Integer> getLevelCounts(String executionId) {
        String sql = "SELECT level, COUNT(*) as count FROM execution_logs WHERE execution_id = ? GROUP BY level";
        Map<String, Integer> counts = new HashMap<>();
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, executionId);
        for (Map<String, Object> row : results) {
            counts.put((String) row.get("level"), ((Number) row.get("count")).intValue());
        }
        return counts;
    }

    private List<String> getNodesList(String executionId) {
        String sql = "SELECT DISTINCT node_id FROM execution_logs WHERE execution_id = ? AND node_id IS NOT NULL";
        return jdbcTemplate.queryForList(sql, String.class, executionId);
    }

    private Long getFirstTimestamp(String executionId) {
        String sql = "SELECT MIN(timestamp) FROM execution_logs WHERE execution_id = ?";
        Long result = jdbcTemplate.queryForObject(sql, Long.class, executionId);
        return result != null ? result : null;
    }

    private Long getLastTimestamp(String executionId) {
        String sql = "SELECT MAX(timestamp) FROM execution_logs WHERE execution_id = ?";
        Long result = jdbcTemplate.queryForObject(sql, Long.class, executionId);
        return result != null ? result : null;
    }
}
