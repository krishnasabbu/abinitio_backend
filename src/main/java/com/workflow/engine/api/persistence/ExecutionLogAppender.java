package com.workflow.engine.api.persistence;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.springframework.jdbc.core.JdbcTemplate;

public class ExecutionLogAppender extends AppenderBase<ILoggingEvent> {

    private JdbcTemplate jdbcTemplate;
    private String executionId;

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (jdbcTemplate == null || executionId == null || executionId.isEmpty()) {
            return;
        }

        try {
            long timestamp = eventObject.getTimeStamp();
            String level = eventObject.getLevel().toString();
            String loggerName = eventObject.getLoggerName();
            String message = eventObject.getFormattedMessage();
            String nodeId = extractNodeId(loggerName);
            String stackTrace = null;

            if (eventObject.getThrowableProxy() != null) {
                stackTrace = eventObject.getThrowableProxy().toString();
            }

            String sql = "INSERT INTO execution_logs (timestamp, level, execution_id, node_id, message, stack_trace) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql, timestamp, level, executionId, nodeId, message, stackTrace);
        } catch (Exception e) {
            addError("Error writing log for execution " + executionId, e);
        }
    }

    private String extractNodeId(String loggerName) {
        if (loggerName != null && loggerName.contains("[")) {
            int start = loggerName.lastIndexOf("[");
            int end = loggerName.lastIndexOf("]");
            if (start != -1 && end != -1 && end > start) {
                return loggerName.substring(start + 1, end);
            }
        }
        return null;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getExecutionId() {
        return executionId;
    }
}
