package com.workflow.engine.service;

import com.workflow.engine.api.dto.NodeStatusDto;
import com.workflow.engine.api.dto.WorkflowExecutionResponseDto;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.GraphValidator;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.planner.ExecutionPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core service for executing workflow definitions.
 *
 * Orchestrates the complete workflow execution lifecycle including validation,
 * execution planning, job launching, and result monitoring. Integrates with
 * Spring Batch for distributed job execution.
 *
 * Process:
 * 1. Validates workflow definition (DAG structure, executors available)
 * 2. Plans execution (builds job and steps from workflow)
 * 3. Launches job with async execution
 * 4. Monitors job status and logs step execution details
 *
 * Thread safety: Thread-safe. Service is stateless and safe for concurrent access.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see GraphValidator
 * @see ExecutionPlanner
 */
@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final ExecutionPlanner executionPlanner;
    private final ExecutionGraphBuilder executionGraphBuilder;
    private final GraphValidator graphValidator;
    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs the WorkflowExecutionService with required dependencies.
     *
     * Initializes the Spring Batch JobLauncher for async execution.
     *
     * @param executionPlanner the planner for converting workflows to jobs
     * @param executionGraphBuilder the builder for creating execution plans
     * @param graphValidator the validator for workflow structure
     * @param jobRepository the Spring Batch job repository
     * @param jdbcTemplate the JDBC template for database operations
     * @throws Exception if JobLauncher initialization fails
     */
    public WorkflowExecutionService(ExecutionPlanner executionPlanner,
                                   ExecutionGraphBuilder executionGraphBuilder,
                                   GraphValidator graphValidator,
                                   JobRepository jobRepository,
                                   JdbcTemplate jdbcTemplate) throws Exception {
        this.executionPlanner = executionPlanner;
        this.executionGraphBuilder = executionGraphBuilder;
        this.graphValidator = graphValidator;
        this.jobRepository = jobRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.jobLauncher = createJobLauncher();
        log.debug("WorkflowExecutionService initialized");
    }

    /**
     * Creates and configures the Spring Batch JobLauncher.
     *
     * @return configured JobLauncher for async execution
     * @throws Exception if launcher configuration fails
     */
    private JobLauncher createJobLauncher() throws Exception {
        log.debug("Creating TaskExecutorJobLauncher");
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();
        log.debug("TaskExecutorJobLauncher created successfully");
        return launcher;
    }

    /**
     * Executes a workflow definition end-to-end.
     *
     * Performs the following steps:
     * 1. Validates the workflow definition
     * 2. Plans job execution from the workflow
     * 3. Launches the job asynchronously
     * 4. Waits for completion and logs results
     *
     * @param workflow the workflow definition to execute
     * @throws IllegalArgumentException if workflow validation fails
     * @throws RuntimeException if job execution fails
     * @throws Exception if job launching fails
     */
    public void executeWorkflow(WorkflowDefinition workflow) throws Exception {
        log.info("workflowId={}, Executing workflow", workflow.getId());
        log.debug("workflowId={}, Starting workflow validation", workflow.getId());

        GraphValidator.ValidationResult validationResult = graphValidator.validate(workflow);

        if (!validationResult.isValid()) {
            log.error("workflowId={}, Workflow validation failed: {}", workflow.getId(), validationResult);
            throw new IllegalArgumentException("Workflow validation failed: " + validationResult.toString());
        }

        log.debug("workflowId={}, Validation passed, building execution plan", workflow.getId());

        Job job = executionPlanner.planExecution(workflow);

        log.debug("workflowId={}, Job built successfully: {}", workflow.getId(), job.getName());

        JobParameters jobParameters = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .addString("workflowId", workflow.getId())
            .toJobParameters();

        log.info("workflowId={}, Launching job execution: {}", workflow.getId(), job.getName());
        JobExecution execution = jobLauncher.run(job, jobParameters);

        log.info("workflowId={}, Job execution completed with status: {}", workflow.getId(), execution.getStatus());
        log.debug("workflowId={}, Exit status: {}", workflow.getId(), execution.getExitStatus().getExitCode());

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            log.info("workflowId={}, Workflow executed successfully!", workflow.getId());
            logStepExecutions(execution, workflow.getId());
        } else if (execution.getStatus() == BatchStatus.FAILED) {
            log.error("workflowId={}, Workflow execution failed!", workflow.getId());
            logStepExecutions(execution, workflow.getId());
            throw new RuntimeException("Workflow execution failed");
        }
    }

    /**
     * Executes a workflow definition and returns execution response with node statuses.
     *
     * @param workflow the workflow definition to execute
     * @return WorkflowExecutionResponseDto containing execution details and node statuses
     * @throws IllegalArgumentException if workflow validation fails
     * @throws RuntimeException if job execution fails
     * @throws Exception if job launching fails
     */
    public WorkflowExecutionResponseDto executeWorkflowWithResponse(WorkflowDefinition workflow) throws Exception {
        log.info("workflowId={}, Executing workflow with response", workflow.getId());
        log.debug("workflowId={}, Starting workflow validation", workflow.getId());

        GraphValidator.ValidationResult validationResult = graphValidator.validate(workflow);

        if (!validationResult.isValid()) {
            log.error("workflowId={}, Workflow validation failed: {}", workflow.getId(), validationResult);
            throw new IllegalArgumentException("Workflow validation failed: " + validationResult.toString());
        }

        log.debug("workflowId={}, Validation passed, building execution plan", workflow.getId());

        String executionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        // Build execution plan first to get total_nodes count
        ExecutionPlan plan = executionGraphBuilder.build(workflow);
        int totalNodes = plan.steps().size();

        persistWorkflowExecutionRecord(executionId, workflow, startTime, totalNodes);

        Job job = executionPlanner.planExecution(workflow, executionId);

        log.debug("workflowId={}, Job built successfully: {}, executionId={}", workflow.getId(), job.getName(), executionId);

        JobParameters jobParameters = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .addString("workflowId", workflow.getId())
            .addString("executionId", executionId)
            .toJobParameters();

        log.info("workflowId={}, executionId={}, Launching job execution: {}", workflow.getId(), executionId, job.getName());
        JobExecution execution = jobLauncher.run(job, jobParameters);

        log.info("workflowId={}, executionId={}, Job execution completed with status: {}", workflow.getId(), executionId, execution.getStatus());
        log.debug("workflowId={}, Exit status: {}", workflow.getId(), execution.getExitStatus().getExitCode());

        WorkflowExecutionResponseDto response = buildExecutionResponse(execution, workflow, executionId);

        persistExecutionResult(execution, executionId, response);

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            log.info("workflowId={}, Workflow executed successfully!", workflow.getId());
            logStepExecutions(execution, workflow.getId());
        } else if (execution.getStatus() == BatchStatus.FAILED) {
            log.error("workflowId={}, Workflow execution failed!", workflow.getId());
            logStepExecutions(execution, workflow.getId());
        }

        return response;
    }

    /**
     * Persists initial workflow execution record to database.
     *
     * @param executionId the unique execution identifier
     * @param workflow the workflow definition
     * @param startTime the execution start time
     * @param totalNodes the total number of nodes in the execution plan
     */
    private void persistWorkflowExecutionRecord(String executionId, WorkflowDefinition workflow, long startTime, int totalNodes) {
        try {
            String id = UUID.randomUUID().toString();
            String workflowId = workflow.getId() != null ? workflow.getId() : "workflow_" + UUID.randomUUID().toString().substring(0, 8);
            String sql = "INSERT INTO workflow_executions (id, execution_id, workflow_name, workflow_id, status, start_time, total_nodes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            int rowsInserted = jdbcTemplate.update(sql, id, executionId, workflow.getName(), workflowId, "running", startTime, totalNodes);
            log.info("Persisted workflow execution record: executionId={}, workflowId={}, totalNodes={}, rows={}",
                executionId, workflowId, totalNodes, rowsInserted);
        } catch (Exception e) {
            log.error("Error persisting workflow execution record: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds execution response DTO from job execution result.
     *
     * @param jobExecution the completed job execution
     * @param workflow the workflow definition
     * @param executionId the execution identifier
     * @return WorkflowExecutionResponseDto with all execution details
     */
    private WorkflowExecutionResponseDto buildExecutionResponse(JobExecution jobExecution, WorkflowDefinition workflow, String executionId) {
        WorkflowExecutionResponseDto response = new WorkflowExecutionResponseDto();

        response.setExecutionId(executionId);
        response.setWorkflowId(workflow.getId());
        response.setWorkflowName(workflow.getName());
        response.setStatus(jobExecution.getStatus().toString());

        if (jobExecution.getStartTime() != null) {
            response.setStartTime(jobExecution.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        }
        if (jobExecution.getEndTime() != null) {
            response.setEndTime(jobExecution.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        }

        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            long durationMillis = Duration.between(
                    jobExecution.getStartTime().toInstant(ZoneOffset.UTC),
                    jobExecution.getEndTime().toInstant(ZoneOffset.UTC)
            ).toMillis();
            response.setExecutionTimeMs(durationMillis);
        }

        List<NodeStatusDto> nodeStatuses = new ArrayList<>();
        int completed = 0;
        int successful = 0;
        int failed = 0;

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            NodeStatusDto nodeStatus = new NodeStatusDto();
            nodeStatus.setNodeName(stepExecution.getStepName());
            nodeStatus.setStatus(stepExecution.getStatus().toString());

            if (jobExecution.getStartTime() != null) {
                nodeStatus.setStartTime(jobExecution.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli());
            }
            if (jobExecution.getEndTime() != null) {
                nodeStatus.setEndTime(jobExecution.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli());
            }

            if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
                long durationMillis = Duration.between(
                        jobExecution.getStartTime().toInstant(ZoneOffset.UTC),
                        jobExecution.getEndTime().toInstant(ZoneOffset.UTC)
                ).toMillis();
                nodeStatus.setExecutionTimeMs(durationMillis);
            }

            nodeStatus.setRecordsRead((long) stepExecution.getReadCount());
            nodeStatus.setRecordsWritten((long) stepExecution.getWriteCount());
            nodeStatus.setSkipCount((long) stepExecution.getSkipCount());

            if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
                nodeStatus.setErrorMessage(stepExecution.getFailureExceptions().get(0).getMessage());
            }

            nodeStatuses.add(nodeStatus);

            if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
                completed++;
                successful++;
            } else if (stepExecution.getStatus() == BatchStatus.FAILED) {
                failed++;
            }
        }

        response.setNodeStatuses(nodeStatuses);
        response.setTotalNodes(jobExecution.getStepExecutions().size());
        response.setCompletedNodes(completed);
        response.setSuccessfulNodes(successful);
        response.setFailedNodes(failed);

        if (jobExecution.getExitStatus().getExitCode() != null && !jobExecution.getExitStatus().getExitCode().equals("COMPLETED")) {
            response.setErrorMessage(jobExecution.getExitStatus().getExitDescription());
        }

        return response;
    }

    private void persistExecutionResult(JobExecution jobExecution, String executionId, WorkflowExecutionResponseDto response) {
        try {
            long endTime = response.getEndTime() != null ? response.getEndTime() : System.currentTimeMillis();
            String status = mapBatchStatusToExecutionStatus(jobExecution.getStatus().toString());

            String errorMessage = null;
            if (jobExecution.getFailureExceptions() != null && !jobExecution.getFailureExceptions().isEmpty()) {
                errorMessage = jobExecution.getFailureExceptions().get(0).getMessage();
            }

            String updateSql = "UPDATE workflow_executions SET status = ?, end_time = ?, " +
                    "completed_nodes = ?, successful_nodes = ?, failed_nodes = ?, " +
                    "total_records = ?, total_execution_time_ms = ?" +
                    (errorMessage != null ? ", error_message = ? " : " ") +
                    "WHERE execution_id = ?";

            long totalRecords = 0;
            for (StepExecution step : jobExecution.getStepExecutions()) {
                totalRecords += step.getReadCount();
            }

            int rowsUpdated;
            if (errorMessage != null) {
                rowsUpdated = jdbcTemplate.update(updateSql, status, endTime,
                        response.getCompletedNodes(), response.getSuccessfulNodes(), response.getFailedNodes(),
                        totalRecords, response.getExecutionTimeMs(), errorMessage, executionId);
            } else {
                rowsUpdated = jdbcTemplate.update(updateSql, status, endTime,
                        response.getCompletedNodes(), response.getSuccessfulNodes(), response.getFailedNodes(),
                        totalRecords, response.getExecutionTimeMs(), executionId);
            }

            log.info("Updated workflow_executions: executionId={}, status={}, rows={}", executionId, status, rowsUpdated);

            persistNodeExecutions(jobExecution, executionId);

        } catch (Exception e) {
            log.error("Error persisting execution result for executionId={}: {}", executionId, e.getMessage(), e);
        }
    }

    private void persistNodeExecutions(JobExecution jobExecution, String executionId) {
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            try {
                String nodeId = stepExecution.getStepName();
                String status = mapBatchStatusToExecutionStatus(stepExecution.getStatus().toString());

                long startTime = stepExecution.getStartTime() != null ?
                        stepExecution.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli() : System.currentTimeMillis();
                long endTime = stepExecution.getEndTime() != null ?
                        stepExecution.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli() : System.currentTimeMillis();
                long executionTimeMs = endTime - startTime;

                String errorMessage = null;
                if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
                    errorMessage = stepExecution.getFailureExceptions().get(0).getMessage();
                }

                String checkSql = "SELECT COUNT(*) FROM node_executions WHERE execution_id = ? AND node_id = ?";
                Integer existingCount = jdbcTemplate.queryForObject(checkSql, Integer.class, executionId, nodeId);

                if (existingCount != null && existingCount > 0) {
                    String updateSql = "UPDATE node_executions SET status = ?, end_time = ?, " +
                            "execution_time_ms = ?, records_processed = ?" +
                            (errorMessage != null ? ", error_message = ? " : " ") +
                            "WHERE execution_id = ? AND node_id = ?";

                    if (errorMessage != null) {
                        jdbcTemplate.update(updateSql, status, endTime, executionTimeMs,
                                stepExecution.getReadCount(), errorMessage, executionId, nodeId);
                    } else {
                        jdbcTemplate.update(updateSql, status, endTime, executionTimeMs,
                                stepExecution.getReadCount(), executionId, nodeId);
                    }
                } else {
                    String insertSql = "INSERT INTO node_executions (id, execution_id, node_id, node_label, node_type, " +
                            "status, start_time, end_time, execution_time_ms, records_processed, error_message) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                    String id = UUID.randomUUID().toString();
                    jdbcTemplate.update(insertSql, id, executionId, nodeId, nodeId, "step",
                            status, startTime, endTime, executionTimeMs, stepExecution.getReadCount(), errorMessage);
                }

                log.debug("Persisted node execution: executionId={}, nodeId={}, status={}", executionId, nodeId, status);
            } catch (Exception e) {
                log.error("Error persisting node execution for node {}: {}", stepExecution.getStepName(), e.getMessage());
            }
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

    /**
     * Logs detailed execution results for all steps in the job.
     *
     * Outputs step-by-step execution metrics including read/write counts,
     * skip counts, duration, and any failure exceptions.
     *
     * @param jobExecution the completed job execution
     * @param workflowId the workflow ID for logging context
     */
    private void logStepExecutions(JobExecution jobExecution, String workflowId) {
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            log.info("workflowId={}, Step: {} Status: {}", workflowId, stepExecution.getStepName(), stepExecution.getStatus());
            log.debug("workflowId={}, Step: {} Read: {} Write: {} Skip: {} Commit: {} Rollback: {}",
                workflowId, stepExecution.getStepName(),
                stepExecution.getReadCount(), stepExecution.getWriteCount(),
                stepExecution.getSkipCount(), stepExecution.getCommitCount(),
                stepExecution.getRollbackCount());

            if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
                if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
                    long durationMillis = Duration.between(
                            stepExecution.getStartTime(),
                            stepExecution.getEndTime()
                    ).toMillis();
                    log.debug("workflowId={}, Step: {} Duration: {}ms", workflowId, stepExecution.getStepName(), durationMillis);
                }

            }
            if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
                log.error("workflowId={}, Step: {} Failures: {}", workflowId, stepExecution.getStepName(), stepExecution.getFailureExceptions());
            }
        }
    }
}
