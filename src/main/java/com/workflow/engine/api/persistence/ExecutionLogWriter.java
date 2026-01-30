package com.workflow.engine.api.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ExecutionLogWriter {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionLogWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final String executionId;

    public ExecutionLogWriter(JdbcTemplate jdbcTemplate, String executionId) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionId = executionId;
    }

    public void writeLog(String level, String message, String nodeId, String stackTrace) {
        try {
            long timestamp = System.currentTimeMillis();
            String datetime = formatTimestamp(timestamp);

            String sql = "INSERT INTO execution_logs (timestamp, datetime, level, execution_id, node_id, message, stack_trace) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql, timestamp, datetime, level, executionId, nodeId, message, stackTrace);
        } catch (Exception e) {
            logger.error("Error writing log for execution {}", executionId, e);
        }
    }

    public void writeLog(String level, String message) {
        writeLog(level, message, null, null);
    }

    public void writeLog(String level, String message, String nodeId) {
        writeLog(level, message, nodeId, null);
    }

    public void writeErrorLog(String message, String nodeId, Exception ex) {
        String stackTrace = null;
        if (ex != null) {
            stackTrace = stackTraceToString(ex);
        }
        writeLog("ERROR", message, nodeId, stackTrace);
    }

    private String formatTimestamp(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_INSTANT);
    }

    private String stackTraceToString(Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");
        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
        }
        return sb.toString();
    }
}
