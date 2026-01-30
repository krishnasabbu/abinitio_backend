package com.workflow.engine.service;

import com.workflow.engine.graph.GraphValidator;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.planner.ExecutionPlanner;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

@Service
public class WorkflowExecutionService {

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
        GraphValidator.ValidationResult validationResult = graphValidator.validate(workflow);

        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Workflow validation failed: " + validationResult.toString());
        }

        Job job = executionPlanner.planExecution(workflow);

        JobParameters jobParameters = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .addString("workflowId", workflow.getId())
            .toJobParameters();

        jobLauncher.run(job, jobParameters);
    }
}
