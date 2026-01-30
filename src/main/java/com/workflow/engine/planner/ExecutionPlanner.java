package com.workflow.engine.planner;

import com.workflow.engine.execution.NodeExecutionContext;
import com.workflow.engine.execution.NodeExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.graph.DagUtils;
import com.workflow.engine.model.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExecutionPlanner {

    private final NodeExecutorRegistry executorRegistry;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public ExecutionPlanner(NodeExecutorRegistry executorRegistry,
                          JobRepository jobRepository,
                          PlatformTransactionManager transactionManager) {
        this.executorRegistry = executorRegistry;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    public Job planExecution(WorkflowDefinition workflow) {
        List<String> sourceNodes = DagUtils.findSourceNodes(workflow);

        if (sourceNodes.isEmpty()) {
            throw new IllegalArgumentException("No source nodes found in workflow");
        }

        Flow mainFlow = buildFlow(workflow, sourceNodes);

        return new JobBuilder(workflow.getName() != null ? workflow.getName() : "workflow-" + workflow.getId(), jobRepository)
            .start(mainFlow)
            .end()
            .build();
    }

    private Flow buildFlow(WorkflowDefinition workflow, List<String> startNodes) {
        if (startNodes.size() == 1) {
            return buildSequentialFlow(workflow, startNodes.get(0));
        } else {
            return buildParallelFlow(workflow, startNodes);
        }
    }

    private Flow buildSequentialFlow(WorkflowDefinition workflow, String startNodeId) {
        NodeDefinition startNode = DagUtils.getNodeById(workflow, startNodeId);
        Step step = createStep(startNode);

        FlowBuilder<Flow> flowBuilder = new FlowBuilder<Flow>("flow-" + startNodeId)
            .start(step);

        List<Edge> outgoingEdges = DagUtils.getOutgoingEdges(workflow, startNodeId);

        if (!outgoingEdges.isEmpty()) {
            for (Edge edge : outgoingEdges) {
                if (edge.isControl()) {
                    Flow nextFlow = buildSequentialFlow(workflow, edge.getTarget());
                    flowBuilder = flowBuilder.next(nextFlow);
                } else {
                    NodeDefinition nextNode = DagUtils.getNodeById(workflow, edge.getTarget());
                    Step nextStep = createStep(nextNode);
                    flowBuilder = flowBuilder.next(nextStep);
                }
            }
        }

        return flowBuilder.build();
    }

    private Flow buildParallelFlow(WorkflowDefinition workflow, List<String> parallelNodes) {
        List<Flow> parallelFlows = parallelNodes.stream()
            .map(nodeId -> buildSequentialFlow(workflow, nodeId))
            .collect(Collectors.toList());

        FlowBuilder<Flow> parallelFlowBuilder = new FlowBuilder<Flow>("parallel-flow");

        Flow[] flowArray = parallelFlows.toArray(new Flow[0]);
        Flow firstFlow = flowArray[0];
        Flow[] remainingFlows = Arrays.copyOfRange(flowArray, 1, flowArray.length);

        Flow splitFlow = parallelFlowBuilder
            .start(firstFlow)
            .split(new SimpleAsyncTaskExecutor())
            .add(remainingFlows)
            .build();

        return splitFlow;
    }

    private Step createStep(NodeDefinition nodeDefinition) {
        if ("Start".equalsIgnoreCase(nodeDefinition.getType())) {
            return createNoOpStep(nodeDefinition);
        }

        NodeExecutor<Object, Object> executor = (NodeExecutor<Object, Object>) executorRegistry.getExecutor(nodeDefinition.getType());

        NodeExecutionContext context = new NodeExecutionContext(nodeDefinition, null);

        Integer chunkSize = 10;
        if (nodeDefinition.getExecutionHints() != null && nodeDefinition.getExecutionHints().getChunkSize() != null) {
            chunkSize = nodeDefinition.getExecutionHints().getChunkSize();
        }

        return new StepBuilder("step-" + nodeDefinition.getId(), jobRepository)
            .<Object, Object>chunk(chunkSize, transactionManager)
            .reader(executor.createReader(context))
            .processor(executor.createProcessor(context))
            .writer(executor.createWriter(context))
            .build();
    }

    private Step createNoOpStep(NodeDefinition nodeDefinition) {
        return new StepBuilder("noop-" + nodeDefinition.getId(), jobRepository)
            .tasklet((contribution, chunkContext) -> null, transactionManager)
            .build();
    }
}
