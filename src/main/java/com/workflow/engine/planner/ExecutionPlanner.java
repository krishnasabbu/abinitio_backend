package com.workflow.engine.planner;

import com.workflow.engine.execution.job.DynamicJobBuilder;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.model.*;
import org.springframework.batch.core.Job;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanner {

    private final ExecutionGraphBuilder executionGraphBuilder;
    private final DynamicJobBuilder dynamicJobBuilder;

    public ExecutionPlanner(ExecutionGraphBuilder executionGraphBuilder,
                          DynamicJobBuilder dynamicJobBuilder) {
        this.executionGraphBuilder = executionGraphBuilder;
        this.dynamicJobBuilder = dynamicJobBuilder;
    }

    public Job planExecution(WorkflowDefinition workflow) {
        ExecutionPlan plan = executionGraphBuilder.build(workflow);
        return dynamicJobBuilder.buildJob(plan);
    }
}
