package com.workflow.engine.api.persistence;

import com.workflow.engine.graph.StepNode;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.util.UUID;

public class PersistenceStepListener implements StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceStepListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final StepNode stepNode;
    private final String executionId;

    public PersistenceStepListener(JdbcTemplate jdbcTemplate, StepNode stepNode, String executionId) {
        this.jdbcTemplate = jdbcTemplate;
        this.stepNode = stepNode;
        this.executionId = executionId;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        logger.info("PersistenceStepListener.beforeStep() called for node '{}', executionId='{}'",
            stepNode.nodeId(), executionId);
        try {
            String nodeId = stepNode.nodeId();
            String nodeLabel = stepNode.nodeId();
            String nodeType = stepNode.nodeType();
            long startTime = System.currentTimeMillis();

            String sql = "INSERT INTO node_executions (id, execution_id, node_id, node_label, node_type, status, start_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            String recordId = UUID.randomUUID().toString();
            int rowsInserted = jdbcTemplate.update(sql, recordId, executionId, nodeId, nodeLabel, nodeType, "running", startTime);

            stepExecution.getExecutionContext().put("nodeExecutionId", recordId);
            stepExecution.getExecutionContext().put("nodeId", nodeId);

            logger.info("Node execution started: node='{}', type='{}', executionId='{}', recordId='{}', rows={}",
                nodeId, nodeType, executionId, recordId, rowsInserted);
        } catch (Exception e) {
            logger.error("Error recording step start for node '{}': {}", stepNode.nodeId(), e.getMessage(), e);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        logger.info("PersistenceStepListener.afterStep() called for node '{}', stepStatus='{}'",
            stepNode.nodeId(), stepExecution.getStatus());
        try {
            String nodeExecutionId = (String) stepExecution.getExecutionContext().get("nodeExecutionId");
            if (nodeExecutionId == null) {
                logger.warn("nodeExecutionId is null for node '{}', skipping update", stepNode.nodeId());
                return stepExecution.getExitStatus();
            }

            long endTime = System.currentTimeMillis();
            long readCount = stepExecution.getReadCount();
            long writeCount = stepExecution.getWriteCount();
            String status = mapBatchStatusToNodeStatus(stepExecution.getStatus().toString());
            String errorMessage = null;

            if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
                Throwable ex = stepExecution.getFailureExceptions().get(0);
                errorMessage = ex.getMessage();
            }

            long startTime = stepExecution.getStartTime() != null ?
                    stepExecution.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli() :
                    endTime;
            long executionTimeMs = endTime - startTime;

            String updateSql = "UPDATE node_executions SET status = ?, end_time = ?, execution_time_ms = ?, records_processed = ? " +
                    (errorMessage != null ? ", error_message = ? " : "") +
                    "WHERE id = ?";

            int rowsUpdated;
            if (errorMessage != null) {
                rowsUpdated = jdbcTemplate.update(updateSql, status, endTime, executionTimeMs, readCount, errorMessage, nodeExecutionId);
            } else {
                rowsUpdated = jdbcTemplate.update(updateSql, status, endTime, executionTimeMs, readCount, nodeExecutionId);
            }

            logger.info("Node execution completed: node='{}', status='{}', records={}, time={}ms, rowsUpdated={}",
                stepNode.nodeId(), status, readCount, executionTimeMs, rowsUpdated);
            return stepExecution.getExitStatus();
        } catch (Exception e) {
            logger.error("Error recording step completion for node '{}': {}", stepNode.nodeId(), e.getMessage(), e);
            return stepExecution.getExitStatus();
        }
    }

    private String mapBatchStatusToNodeStatus(String batchStatus) {
        return switch (batchStatus) {
            case "COMPLETED" -> "success";
            case "FAILED" -> "failed";
            case "STOPPED" -> "stopped";
            default -> "running";
        };
    }
}
