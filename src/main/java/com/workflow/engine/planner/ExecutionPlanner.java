package com.workflow.engine.planner;

import com.workflow.engine.api.persistence.PersistenceJobListener;
import com.workflow.engine.execution.job.DynamicJobBuilder;
import com.workflow.engine.execution.job.StepFactory;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.model.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanner {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionPlanner.class);

    private final ExecutionGraphBuilder executionGraphBuilder;
    private final DynamicJobBuilder dynamicJobBuilder;
    private final StepFactory stepFactory;
    private final JdbcTemplate jdbcTemplate;

    public ExecutionPlanner(ExecutionGraphBuilder executionGraphBuilder,
                          DynamicJobBuilder dynamicJobBuilder,
                          StepFactory stepFactory,
                          JdbcTemplate jdbcTemplate) {
        this.executionGraphBuilder = executionGraphBuilder;
        this.dynamicJobBuilder = dynamicJobBuilder;
        this.stepFactory = stepFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Job planExecution(WorkflowDefinition workflow) {
        ExecutionPlan plan = executionGraphBuilder.build(workflow);
        return dynamicJobBuilder.buildJob(plan);
    }

    public Job planExecution(WorkflowDefinition workflow, String executionId) {
        ExecutionPlan plan = executionGraphBuilder.build(workflow);
        stepFactory.setApiListenerContext(jdbcTemplate, executionId);
        Job job = dynamicJobBuilder.buildJob(plan);

        if (job instanceof AbstractJob abstractJob) {
            abstractJob.registerJobExecutionListener(new PersistenceJobListener(jdbcTemplate, executionId));
            logger.info("Registered PersistenceJobListener for executionId: {}", executionId);
        } else {
            logger.warn("Cannot register job listener - job type {} not supported", job.getClass().getName());
        }

        return job;
    }
}

