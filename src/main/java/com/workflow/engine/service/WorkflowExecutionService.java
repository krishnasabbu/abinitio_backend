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

@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);

    private final ExecutionPlanner executionPlanner;
    private final GraphValidator graphValidator;
    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;

    public WorkflowExecutionService(ExecutionPlanner executionPlanner,
                                   GraphValidator graphValidator,
                                   JobRepository jobRepository) throws Exception {
        this.executionPlanner = executionPlanner;
        this.graphValidator = graphValidator;
        this.jobRepository = jobRepository;
        this.jobLauncher = createJobLauncher();
    }

    private JobLauncher createJobLauncher() throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();
        return launcher;
    }

    public void executeWorkflow(WorkflowDefinition workflow) throws Exception {
        log.info("Starting workflow validation: {}", workflow.getId());

        GraphValidator.ValidationResult validationResult = graphValidator.validate(workflow);

        if (!validationResult.isValid()) {
            log.error("Workflow validation failed: {}", validationResult);
            throw new IllegalArgumentException("Workflow validation failed: " + validationResult.toString());
        }

        log.info("Workflow validation passed. Building execution plan...");

        Job job = executionPlanner.planExecution(workflow);

        log.info("Job built successfully: {}", job.getName());

        JobParameters jobParameters = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .addString("workflowId", workflow.getId())
            .toJobParameters();

        log.info("Launching job execution...");
        JobExecution execution = jobLauncher.run(job, jobParameters);

        log.info("Job execution completed with status: {}", execution.getStatus());
        log.info("Exit status: {}", execution.getExitStatus().getExitCode());

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Workflow executed successfully!");
            logStepExecutions(execution);
        } else if (execution.getStatus() == BatchStatus.FAILED) {
            log.error("Workflow execution failed!");
            logStepExecutions(execution);
            throw new RuntimeException("Workflow execution failed");
        }
    }

    private void logStepExecutions(JobExecution jobExecution) {
        log.info("\n" + "=".repeat(80));
        log.info("STEP EXECUTION SUMMARY");
        log.info("=".repeat(80));

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            log.info("\nStep: {}", stepExecution.getStepName());
            log.info("  Status: {}", stepExecution.getStatus());
            log.info("  Read Count: {}", stepExecution.getReadCount());
            log.info("  Write Count: {}", stepExecution.getWriteCount());
            log.info("  Skip Count: {}", stepExecution.getSkipCount());
            log.info("  Commit Count: {}", stepExecution.getCommitCount());
            log.info("  Rollback Count: {}", stepExecution.getRollbackCount());
            if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
                long duration = stepExecution.getEndTime().getTime() - stepExecution.getStartTime().getTime();
                log.info("  Duration: {}ms", duration);
            }
            if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
                log.error("  Failures: {}", stepExecution.getFailureExceptions());
            }
        }

        log.info("\n" + "=".repeat(80));
    }
}
