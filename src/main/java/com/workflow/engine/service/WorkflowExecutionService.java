package com.workflow.engine.service;

import com.workflow.engine.graph.GraphValidator;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.planner.ExecutionPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
    private final GraphValidator graphValidator;
    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;

    /**
     * Constructs the WorkflowExecutionService with required dependencies.
     *
     * Initializes the Spring Batch JobLauncher for async execution.
     *
     * @param executionPlanner the planner for converting workflows to jobs
     * @param graphValidator the validator for workflow structure
     * @param jobRepository the Spring Batch job repository
     * @throws Exception if JobLauncher initialization fails
     */
    public WorkflowExecutionService(ExecutionPlanner executionPlanner,
                                   GraphValidator graphValidator,
                                   JobRepository jobRepository) throws Exception {
        this.executionPlanner = executionPlanner;
        this.graphValidator = graphValidator;
        this.jobRepository = jobRepository;
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
