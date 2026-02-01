package com.workflow.engine.planner;

import com.workflow.engine.api.persistence.PersistenceJobListener;
import com.workflow.engine.execution.job.DynamicJobBuilder;
import com.workflow.engine.execution.job.StepFactory;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.model.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanner {

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

        if (job instanceof SimpleJob simpleJob) {
            simpleJob.registerJobExecutionListener(new PersistenceJobListener(jdbcTemplate, executionId));
        }

        return job;
    }
}

