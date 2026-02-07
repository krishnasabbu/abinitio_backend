package com.workflow.engine.api.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.util.DelimitedDataTransformer;
import com.workflow.engine.execution.job.OutputCollectingWriter;
import com.workflow.engine.graph.StepNode;
import com.workflow.engine.repository.NodeOutputDataRepository;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PersistenceStepListener implements StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceStepListener.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final StepNode stepNode;
    private final String executionId;
    private final OutputCollectingWriter<?> collectingWriter;
    private final NodeOutputDataRepository outputDataRepository;
    private final ExecutionLogWriter logWriter;

    public PersistenceStepListener(JdbcTemplate jdbcTemplate, StepNode stepNode, String executionId) {
        this(jdbcTemplate, stepNode, executionId, null, null);
    }

    public PersistenceStepListener(JdbcTemplate jdbcTemplate, StepNode stepNode, String executionId,
                                    OutputCollectingWriter<?> collectingWriter, NodeOutputDataRepository outputDataRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.stepNode = stepNode;
        this.executionId = executionId;
        this.collectingWriter = collectingWriter;
        this.outputDataRepository = outputDataRepository;
        this.logWriter = new ExecutionLogWriter(jdbcTemplate, executionId);
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

            logWriter.writeLog("INFO", "Node '" + nodeId + "' (" + nodeType + ") execution started", nodeId);

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
            long inputRecords = stepExecution.getReadCount();
            long outputRecords = stepExecution.getWriteCount();
            long recordsProcessed = stepExecution.getReadCount();
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

            double recordsPerSecond = 0.0;
            if (executionTimeMs > 0 && recordsProcessed > 0) {
                recordsPerSecond = (recordsProcessed * 1000.0) / executionTimeMs;
            }

            String updateSql = "UPDATE node_executions SET status = ?, end_time = ?, execution_time_ms = ?, input_records = ?, output_records = ?, records_processed = ?, records_per_second = ? " +
                    (errorMessage != null ? ", error_message = ? " : "") +
                    "WHERE id = ?";

            int rowsUpdated;
            if (errorMessage != null) {
                rowsUpdated = jdbcTemplate.update(updateSql, status, endTime, executionTimeMs, inputRecords, outputRecords, recordsProcessed, recordsPerSecond, errorMessage, nodeExecutionId);
            } else {
                rowsUpdated = jdbcTemplate.update(updateSql, status, endTime, executionTimeMs, inputRecords, outputRecords, recordsProcessed, recordsPerSecond, nodeExecutionId);
            }

            logger.info("Node execution completed: node='{}', status='{}', input={}, output={}, time={}ms, rowsUpdated={}",
                stepNode.nodeId(), status, inputRecords, outputRecords, executionTimeMs, rowsUpdated);

            if (errorMessage != null) {
                logWriter.writeLog("ERROR", "Node '" + stepNode.nodeId() + "' failed: " + errorMessage, stepNode.nodeId());
            } else {
                logWriter.writeLog("INFO",
                    "Node '" + stepNode.nodeId() + "' completed successfully (input=" + inputRecords + ", output=" + outputRecords + ", time=" + executionTimeMs + "ms)",
                    stepNode.nodeId());
            }

            persistOutputData(stepNode.nodeId(), stepNode.nodeType());

            return stepExecution.getExitStatus();
        } catch (Exception e) {
            logger.error("Error recording step completion for node '{}': {}", stepNode.nodeId(), e.getMessage(), e);
            return stepExecution.getExitStatus();
        }
    }

    private void persistOutputData(String nodeId, String nodeType) {
        if (collectingWriter == null || outputDataRepository == null) {
            return;
        }

        try {
            List<Map<String, Object>> outputItems = collectingWriter.getCollectedItems();
            if (outputItems.isEmpty()) {
                return;
            }

            outputDataRepository.saveAll(executionId, nodeId, outputItems);

            Map<String, Object> summary = DelimitedDataTransformer.buildOutputSummary(nodeId, nodeType, outputItems);
            String summaryJson = objectMapper.writeValueAsString(summary);
            outputDataRepository.updateOutputSummary(executionId, nodeId, summaryJson);

            logger.info("Persisted {} output records and summary for node '{}' in execution '{}'",
                outputItems.size(), nodeId, executionId);
        } catch (Exception e) {
            logger.warn("Failed to persist output data for node '{}': {}", nodeId, e.getMessage());
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
