package com.workflow.engine.execution.job;

import com.workflow.engine.core.MdcTaskDecorator;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.StepNode;
import com.workflow.engine.model.ExecutionMode;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DynamicJobBuilder {

    private final StepFactory stepFactory;
    private final JobRepository jobRepository;

    public DynamicJobBuilder(StepFactory stepFactory, JobRepository jobRepository) {
        this.stepFactory = stepFactory;
        this.jobRepository = jobRepository;
    }

    public Job buildJob(ExecutionPlan plan) {
        String jobName = "workflow-" + UUID.randomUUID();

        Map<String, Step> stepMap = new HashMap<>();
        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            Step step = stepFactory.buildStep(entry.getValue());
            stepMap.put(entry.getKey(), step);
        }

        Flow mainFlow = buildMainFlow(plan, stepMap);

        return new JobBuilder(jobName, jobRepository)
            .incrementer(new RunIdIncrementer())
            .preventRestart()
            .start(mainFlow)
            .end()
            .build();
    }

    private Flow buildMainFlow(ExecutionPlan plan, Map<String, Step> stepMap) {
        List<String> entryStepIds = plan.entryStepIds();

        if (entryStepIds.isEmpty()) {
            throw new IllegalStateException("Execution plan has no entry steps");
        }

        if (entryStepIds.size() == 1) {
            return buildFlowFromStep(entryStepIds.get(0), plan, stepMap, new HashSet<>());
        }

        List<Flow> entryFlows = new ArrayList<>();
        for (String entryStepId : entryStepIds) {
            Flow flow = buildFlowFromStep(entryStepId, plan, stepMap, new HashSet<>());
            entryFlows.add(flow);
        }

        return createSplitFlow(entryFlows, "main-split");
    }

    private Flow buildFlowFromStep(String stepId, ExecutionPlan plan, Map<String, Step> stepMap, Set<String> visited) {
        if (visited.contains(stepId)) {
            return null;
        }
        visited.add(stepId);

        Step step = stepMap.get(stepId);
        StepNode stepNode = plan.steps().get(stepId);

        if (step == null || stepNode == null) {
            throw new IllegalStateException("Step not found: " + stepId);
        }

        FlowBuilder<SimpleFlow> flowBuilder = new FlowBuilder<>(stepId + "-flow");
        flowBuilder.start(step);

        List<String> nextSteps = stepNode.nextSteps();
        List<String> errorSteps = stepNode.errorSteps();

        if (errorSteps != null && !errorSteps.isEmpty()) {
            for (String errorStepId : errorSteps) {
                if (!visited.contains(errorStepId)) {
                    Flow errorFlow = buildFlowFromStep(errorStepId, plan, stepMap, new HashSet<>(visited));
                    if (errorFlow != null) {
                        flowBuilder.on("FAILED").to(errorFlow);
                    }
                }
            }
        }

        if (nextSteps == null || nextSteps.isEmpty()) {
            flowBuilder.on("*").end();
            return flowBuilder.build();
        }

        if (nextSteps.size() == 1) {
            String nextStepId = nextSteps.get(0);
            if (!visited.contains(nextStepId)) {
                Flow nextFlow = buildFlowFromStep(nextStepId, plan, stepMap, new HashSet<>(visited));
                if (nextFlow != null) {
                    flowBuilder.on("*").to(nextFlow);
                }
            }
        } else {
            List<Flow> branchFlows = new ArrayList<>();
            for (String nextStepId : nextSteps) {
                if (!visited.contains(nextStepId)) {
                    Flow branchFlow = buildFlowFromStep(nextStepId, plan, stepMap, new HashSet<>(visited));
                    if (branchFlow != null) {
                        branchFlows.add(branchFlow);
                    }
                }
            }

            if (!branchFlows.isEmpty()) {
                StepNode currentNode = plan.steps().get(stepId);
                boolean useParallel = currentNode.executionHints() != null &&
                    currentNode.executionHints().getMode() == ExecutionMode.PARALLEL;

                if (useParallel) {
                    Flow splitFlow = createSplitFlow(branchFlows, stepId + "-split");
                    flowBuilder.on("*").to(splitFlow);
                } else {
                    for (int i = 0; i < branchFlows.size(); i++) {
                        if (i == 0) {
                            flowBuilder.on("*").to(branchFlows.get(i));
                        }
                    }
                }
            }
        }

        flowBuilder.on("*").end();
        return flowBuilder.build();
    }

    private Flow createSplitFlow(List<Flow> flows, String flowName) {
        if (flows.isEmpty()) {
            throw new IllegalStateException("Cannot create split flow with empty flows");
        }

        if (flows.size() == 1) {
            return flows.get(0);
        }

        FlowBuilder<SimpleFlow> splitBuilder = new FlowBuilder<>(flowName);
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        taskExecutor.setConcurrencyLimit(flows.size());
        taskExecutor.setTaskDecorator(new MdcTaskDecorator());

        Flow[] flowArray = flows.toArray(new Flow[0]);

        splitBuilder.split(taskExecutor).add(flowArray);

        return splitBuilder.build();
    }

    private Flow buildJoinFlow(StepNode joinNode, ExecutionPlan plan, Map<String, Step> stepMap,
                                Set<String> visited) {
        List<String> upstreamSteps = findUpstreamSteps(joinNode.nodeId(), plan);

        List<Flow> upstreamFlows = new ArrayList<>();
        for (String upstreamStepId : upstreamSteps) {
            if (!visited.contains(upstreamStepId)) {
                Flow flow = buildFlowFromStep(upstreamStepId, plan, stepMap, new HashSet<>(visited));
                if (flow != null) {
                    upstreamFlows.add(flow);
                }
            }
        }

        if (upstreamFlows.isEmpty()) {
            return buildFlowFromStep(joinNode.nodeId(), plan, stepMap, visited);
        }

        Flow joinFlow = createSplitFlow(upstreamFlows, joinNode.nodeId() + "-join-upstream");

        Step joinStep = stepMap.get(joinNode.nodeId());
        FlowBuilder<SimpleFlow> joinFlowBuilder = new FlowBuilder<>(joinNode.nodeId() + "-join-flow");
        joinFlowBuilder.start(joinFlow).on("*").to(joinStep);

        List<String> nextSteps = joinNode.nextSteps();
        if (nextSteps != null && !nextSteps.isEmpty()) {
            for (String nextStepId : nextSteps) {
                if (!visited.contains(nextStepId)) {
                    Flow nextFlow = buildFlowFromStep(nextStepId, plan, stepMap, new HashSet<>(visited));
                    if (nextFlow != null) {
                        joinFlowBuilder.on("*").to(nextFlow);
                    }
                }
            }
        }

        joinFlowBuilder.on("*").end();
        return joinFlowBuilder.build();
    }

    private List<String> findUpstreamSteps(String joinNodeId, ExecutionPlan plan) {
        List<String> upstreamSteps = new ArrayList<>();

        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            StepNode node = entry.getValue();
            if (node.nextSteps() != null && node.nextSteps().contains(joinNodeId)) {
                upstreamSteps.add(entry.getKey());
            }
        }

        return upstreamSteps;
    }
}
