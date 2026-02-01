package com.workflow.engine.api.persistence;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class PersistenceJobListener implements JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceJobListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final String executionId;

    public PersistenceJobListener(JdbcTemplate jdbcTemplate, String executionId) {
        this.jdbcTemplate = jdbcTemplate;
        this.executionId = executionId;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("PersistenceJobListener.beforeJob() called for executionId: {}", executionId);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        logger.info("PersistenceJobListener.afterJob() called for executionId: {}, jobStatus: {}",
            executionId, jobExecution.getStatus());
        try {
            long endTime = System.currentTimeMillis();

            // Check if cancel was requested
            String currentStatusSql = "SELECT status FROM workflow_executions WHERE execution_id = ?";
            String currentStatus = jdbcTemplate.queryForObject(currentStatusSql, String.class, executionId);

            String finalStatus;
            if ("cancel_requested".equals(currentStatus)) {
                finalStatus = "cancelled";
            } else {
                finalStatus = mapBatchStatusToExecutionStatus(jobExecution.getStatus().toString());
            }

            String errorMessage = null;
            if (jobExecution.getFailureExceptions() != null && !jobExecution.getFailureExceptions().isEmpty()) {
                Throwable ex = jobExecution.getFailureExceptions().get(0);
                errorMessage = ex.getMessage();
            }

            String updateSql = "UPDATE workflow_executions SET status = ?, end_time = ? " +
                    (errorMessage != null ? ", error_message = ? " : "") +
                    "WHERE execution_id = ?";

            int rowsUpdated;
            if (errorMessage != null) {
                rowsUpdated = jdbcTemplate.update(updateSql, finalStatus, endTime, errorMessage, executionId);
            } else {
                rowsUpdated = jdbcTemplate.update(updateSql, finalStatus, endTime, executionId);
            }

            logger.info("Updated workflow_executions: executionId='{}', status='{}', endTime={}, rowsUpdated={}",
                executionId, finalStatus, endTime, rowsUpdated);

            calculateAndUpdateTotals(jobExecution);

            logger.info("Job completed for execution: {}, status: {}", executionId, finalStatus);
        } catch (Exception e) {
            logger.error("Error updating job execution record for {}", executionId, e);
        }
    }

    private void calculateAndUpdateTotals(JobExecution jobExecution) {
        try {
            String successSql = "SELECT COUNT(*) FROM node_executions WHERE execution_id = ? AND status = 'success'";
            Integer successfulNodes = jdbcTemplate.queryForObject(successSql, Integer.class, executionId);

            String failedSql = "SELECT COUNT(*) FROM node_executions WHERE execution_id = ? AND status = 'failed'";
            Integer failedNodes = jdbcTemplate.queryForObject(failedSql, Integer.class, executionId);

            Integer completedNodes = (successfulNodes != null ? successfulNodes : 0) + (failedNodes != null ? failedNodes : 0);

            String sumRecordsSql = "SELECT COALESCE(SUM(records_processed), 0) FROM node_executions WHERE execution_id = ?";
            Long totalRecords = jdbcTemplate.queryForObject(sumRecordsSql, Long.class, executionId);

            String sumTimeSql = "SELECT COALESCE(SUM(execution_time_ms), 0) FROM node_executions WHERE execution_id = ?";
            Long totalTime = jdbcTemplate.queryForObject(sumTimeSql, Long.class, executionId);

            String updateTotalsSql = "UPDATE workflow_executions SET completed_nodes = ?, " +
                    "successful_nodes = ?, failed_nodes = ?, total_records = ?, total_execution_time_ms = ? " +
                    "WHERE execution_id = ?";

            int updated = jdbcTemplate.update(updateTotalsSql,
                    completedNodes,
                    successfulNodes != null ? successfulNodes : 0,
                    failedNodes != null ? failedNodes : 0,
                    totalRecords != null ? totalRecords : 0,
                    totalTime != null ? totalTime : 0,
                    executionId);

            logger.info("Updated execution metrics for {}: completed={}, successful={}, failed={}, records={}, time={}ms",
                    executionId, completedNodes, successfulNodes, failedNodes, totalRecords, totalTime);
        } catch (Exception e) {
            logger.error("Error calculating totals for execution {}", executionId, e);
        }
    }

    private String mapBatchStatusToExecutionStatus(String batchStatus) {
        return switch (batchStatus) {
            case "COMPLETED" -> "success";
            case "FAILED" -> "failed";
            case "STOPPED" -> "cancelled";
            default -> "running";
        };
    }
}
