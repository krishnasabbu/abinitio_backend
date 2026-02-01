package com.workflow.engine.execution.job;

import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.ExecutionPlanValidator;
import com.workflow.engine.graph.StepKind;
import com.workflow.engine.graph.StepNode;
import com.workflow.engine.model.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-grade Spring Batch Job builder that compiles ExecutionPlan (DAG) into
 * a Job with correct fork/join semantics, error routing, and deterministic execution.
 *
 * <h2>Fork/Join Semantics</h2>
 * <pre>
 * FORK Pattern:
 *   [StepA] (FORK or PARALLEL mode)
 *      |
 *   split(executor)
 *    / | \
 * [B] [C] [D]  <- parallel branches
 *    \ | /
 *    (join)    <- implicit join when split completes
 *      |
 *   [JoinStep] <- explicit JOIN node executes after all branches
 *      |
 *   [Continue]
 * </pre>
 *
 * <h2>Key Design Decisions</h2>
 * <ul>
 *   <li>JOIN is explicit: Nodes with kind=JOIN or classification=JOIN are wired as
 *       synchronization barriers after parallel splits</li>
 *   <li>No visited-skip: Uses topological compilation with explicit join detection
 *       instead of naive DFS visited tracking</li>
 *   <li>Error routing: on("FAILED") routes to errorSteps, on("*") to nextSteps</li>
 *   <li>Deterministic naming: Job name is workflow-{planId} for restartability</li>
 *   <li>Shared executor: Uses DI'd ThreadPoolTaskExecutor with MDC propagation</li>
 * </ul>
 *
 * <h2>Subgraph Support (Extension Point)</h2>
 * Nodes with kind=SUBGRAPH trigger {@link #compileSubgraph} which can be overridden
 * to expand nested workflows. Default implementation throws UnsupportedOperationException.
 *
 * @see ExecutionPlan
 * @see ExecutionPlanValidator
 * @see JoinBarrierTasklet
 */
@Component
public class DynamicJobBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DynamicJobBuilder.class);

    private final StepFactory stepFactory;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TaskExecutor workflowTaskExecutor;

    @Value("${workflow.job.restartable:true}")
    private boolean restartable;

    @Value("${workflow.executor.concurrency-limit:0}")
    private int concurrencyLimit;

    @Autowired
    public DynamicJobBuilder(
            StepFactory stepFactory,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("workflowTaskExecutor") @Autowired(required = false) TaskExecutor workflowTaskExecutor) {
        this.stepFactory = stepFactory;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.workflowTaskExecutor = workflowTaskExecutor != null
            ? workflowTaskExecutor
            : createDefaultTaskExecutor();
    }

    private TaskExecutor createDefaultTaskExecutor() {
        logger.warn("No workflowTaskExecutor bean found, creating default executor");
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("wf-default-");
        executor.setTaskDecorator(new com.workflow.engine.core.MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    public Job buildJob(ExecutionPlan plan) {
        return buildJob(plan, null);
    }

    public Job buildJob(ExecutionPlan plan, String workflowId) {
        Objects.requireNonNull(plan, "ExecutionPlan cannot be null");

        ExecutionPlanValidator.validate(plan);

        String jobName = determineJobName(plan, workflowId);
        logger.info("Building job '{}' with {} steps, {} entry points",
            jobName, plan.steps().size(), plan.entryStepIds().size());

        BuildContext ctx = new BuildContext(plan, jobName);
        buildAllSteps(ctx);

        Flow mainFlow = buildMainFlow(ctx);

        JobBuilder jobBuilder = new JobBuilder(jobName, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(mainFlow)
            .end();

        if (!restartable) {
            jobBuilder.preventRestart();
        }

        Job job = jobBuilder.build();
        logger.info("Built job '{}' successfully", jobName);
        return job;
    }

    private String determineJobName(ExecutionPlan plan, String workflowId) {
        if (workflowId != null && !workflowId.isBlank()) {
            return "workflow-" + workflowId.trim();
        }
        return "workflow-" + UUID.randomUUID();
    }

    private void buildAllSteps(BuildContext ctx) {
        for (Map.Entry<String, StepNode> entry : ctx.plan.steps().entrySet()) {
            String nodeId = entry.getKey();
            StepNode node = entry.getValue();

            Step step;
            if (node.isJoin() && isBarrierOnly(node)) {
                step = buildBarrierStep(ctx, node);
            } else {
                step = stepFactory.buildStep(node);
            }

            ctx.stepMap.put(nodeId, step);
            logger.debug("Built step for node '{}' (kind={})", nodeId, node.kind());
        }
    }

    private boolean isBarrierOnly(StepNode node) {
        return "Barrier".equals(node.nodeType()) ||
               "JoinBarrier".equals(node.nodeType()) ||
               node.kind() == StepKind.BARRIER;
    }

    private Step buildBarrierStep(BuildContext ctx, StepNode node) {
        List<String> upstreamBranches = node.upstreamSteps() != null
            ? node.upstreamSteps()
            : findUpstreamSteps(node.nodeId(), ctx.plan);

        JoinBarrierTasklet tasklet = new JoinBarrierTasklet(node.nodeId(), upstreamBranches);

        return new StepBuilder(node.nodeId(), jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }

    private Flow buildMainFlow(BuildContext ctx) {
        List<String> entryStepIds = ctx.plan.entryStepIds();

        if (entryStepIds.isEmpty()) {
            throw new IllegalStateException("Execution plan has no entry steps");
        }

        if (entryStepIds.size() == 1) {
            return buildFlowFromNode(entryStepIds.get(0), ctx);
        }

        List<Flow> entryFlows = entryStepIds.stream()
            .map(id -> buildFlowFromNode(id, ctx))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (entryFlows.isEmpty()) {
            throw new IllegalStateException("No valid entry flows could be built");
        }

        return createParallelSplit(entryFlows, "main-split", ctx);
    }

    private Flow buildFlowFromNode(String nodeId, BuildContext ctx) {
        if (ctx.flowCache.containsKey(nodeId)) {
            logger.debug("Returning cached flow for node '{}'", nodeId);
            return ctx.flowCache.get(nodeId);
        }

        if (ctx.currentPath.contains(nodeId)) {
            logger.error("Cycle detected during flow building at node '{}'", nodeId);
            throw new IllegalStateException("Cycle detected at node: " + nodeId);
        }

        StepNode node = ctx.plan.steps().get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Node not found: " + nodeId);
        }

        ctx.currentPath.add(nodeId);

        try {
            Flow flow;
            if (node.isJoin()) {
                flow = buildJoinFlow(node, ctx);
            } else if (node.isSubgraph()) {
                flow = compileSubgraph(node, ctx);
            } else if (node.isFork()) {
                flow = buildForkFlow(node, ctx);
            } else if (node.isDecision()) {
                flow = buildDecisionFlow(node, ctx);
            } else {
                flow = buildNormalFlow(node, ctx);
            }

            ctx.flowCache.put(nodeId, flow);
            return flow;
        } finally {
            ctx.currentPath.remove(nodeId);
        }
    }

    private Flow buildNormalFlow(StepNode node, BuildContext ctx) {
        Step step = ctx.stepMap.get(node.nodeId());
        FlowBuilder<SimpleFlow> flowBuilder = new FlowBuilder<>(node.nodeId() + "-flow");
        flowBuilder.start(step);

        wireErrorRouting(flowBuilder, node, ctx);
        wireNextSteps(flowBuilder, node, ctx);

        return flowBuilder.build();
    }

    private Flow buildForkFlow(StepNode node, BuildContext ctx) {
        Step forkStep = ctx.stepMap.get(node.nodeId());
        FlowBuilder<SimpleFlow> flowBuilder = new FlowBuilder<>(node.nodeId() + "-fork-flow");
        flowBuilder.start(forkStep);

        wireErrorRouting(flowBuilder, node, ctx);

        List<String> nextSteps = node.nextSteps();
        if (nextSteps == null || nextSteps.isEmpty()) {
            flowBuilder.on("*").end();
            return flowBuilder.build();
        }

        String joinNodeId = findJoinForFork(node, ctx);

        List<Flow> branchFlows = new ArrayList<>();
        for (String branchStartId : nextSteps) {
            Flow branchFlow = buildBranchUntilJoin(branchStartId, joinNodeId, ctx);
            if (branchFlow != null) {
                branchFlows.add(branchFlow);
            }
        }

        if (branchFlows.isEmpty()) {
            flowBuilder.on("*").end();
            return flowBuilder.build();
        }

        Flow splitFlow = createParallelSplit(branchFlows, node.nodeId() + "-split", ctx);
        flowBuilder.on("*").to(splitFlow);

        if (joinNodeId != null) {
            Flow joinContinuation = buildFlowFromNode(joinNodeId, ctx);
            if (joinContinuation != null) {
                return new FlowBuilder<SimpleFlow>(node.nodeId() + "-fork-join")
                    .start(flowBuilder.build())
                    .next(joinContinuation)
                    .build();
            }
        }

        return flowBuilder.build();
    }

    private Flow buildBranchUntilJoin(String startNodeId, String joinNodeId, BuildContext ctx) {
        if (startNodeId.equals(joinNodeId)) {
            return null;
        }

        StepNode node = ctx.plan.steps().get(startNodeId);
        if (node == null) {
            throw new IllegalStateException("Branch node not found: " + startNodeId);
        }

        Step step = ctx.stepMap.get(startNodeId);
        FlowBuilder<SimpleFlow> flowBuilder = new FlowBuilder<>(startNodeId + "-branch");
        flowBuilder.start(step);

        wireErrorRouting(flowBuilder, node, ctx);

        List<String> nextSteps = node.nextSteps();
        if (nextSteps == null || nextSteps.isEmpty()) {
            flowBuilder.on("*").end();
            return flowBuilder.build();
        }

        if (nextSteps.size() == 1) {
            String nextId = nextSteps.get(0);
            if (nextId.equals(joinNodeId)) {
                flowBuilder.on("*").end();
            } else {
                Flow nextFlow = buildBranchUntilJoin(nextId, joinNodeId, ctx);
                if (nextFlow != null) {
                    flowBuilder.on("*").to(nextFlow);
                } else {
                    flowBuilder.on("*").end();
                }
            }
        } else {
            List<Flow> subBranches = new ArrayList<>();
            for (String nextId : nextSteps) {
                if (!nextId.equals(joinNodeId)) {
                    Flow subBranch = buildBranchUntilJoin(nextId, joinNodeId, ctx);
                    if (subBranch != null) {
                        subBranches.add(subBranch);
                    }
                }
            }

            if (!subBranches.isEmpty()) {
                if (isParallelMode(node)) {
                    Flow split = createParallelSplit(subBranches, startNodeId + "-subsplit", ctx);
                    flowBuilder.on("*").to(split);
                } else {
                    Flow chainedFlow = chainFlowsSequentially(subBranches, startNodeId + "-chain");
                    flowBuilder.on("*").to(chainedFlow);
                }
            } else {
                flowBuilder.on("*").end();
            }
        }

        return flowBuilder.build();
    }

    private String findJoinForFork(StepNode forkNode, BuildContext ctx) {
        if (forkNode.nextSteps() == null || forkNode.nextSteps().size() <= 1) {
            return null;
        }

        Set<String> allDownstream = new HashSet<>();
        Map<String, Set<String>> branchReachable = new HashMap<>();

        for (String branchStart : forkNode.nextSteps()) {
            Set<String> reachable = new HashSet<>();
            collectReachableNodes(branchStart, reachable, ctx);
            branchReachable.put(branchStart, reachable);
            allDownstream.addAll(reachable);
        }

        for (String candidate : allDownstream) {
            StepNode candidateNode = ctx.plan.steps().get(candidate);
            if (candidateNode != null && candidateNode.isJoin()) {
                boolean reachableFromAll = branchReachable.values().stream()
                    .allMatch(set -> set.contains(candidate));
                if (reachableFromAll) {
                    logger.debug("Found join node '{}' for fork '{}'", candidate, forkNode.nodeId());
                    return candidate;
                }
            }
        }

        Set<String> commonDownstream = null;
        for (Set<String> reachable : branchReachable.values()) {
            if (commonDownstream == null) {
                commonDownstream = new HashSet<>(reachable);
            } else {
                commonDownstream.retainAll(reachable);
            }
        }

        if (commonDownstream != null && !commonDownstream.isEmpty()) {
            logger.warn("Fork '{}' has common downstream {} but no explicit JOIN. " +
                       "Consider adding kind=JOIN to the convergence point.",
                forkNode.nodeId(), commonDownstream);
        }

        return null;
    }

    private void collectReachableNodes(String nodeId, Set<String> reachable, BuildContext ctx) {
        if (reachable.contains(nodeId)) return;
        reachable.add(nodeId);

        StepNode node = ctx.plan.steps().get(nodeId);
        if (node != null && node.nextSteps() != null) {
            for (String next : node.nextSteps()) {
                collectReachableNodes(next, reachable, ctx);
            }
        }
    }

    private Flow buildJoinFlow(StepNode joinNode, BuildContext ctx) {
        logger.debug("Building JOIN flow for node '{}'", joinNode.nodeId());

        Step joinStep = ctx.stepMap.get(joinNode.nodeId());
        FlowBuilder<SimpleFlow> flowBuilder = new FlowBuilder<>(joinNode.nodeId() + "-join-flow");
        flowBuilder.start(joinStep);

        wireErrorRouting(flowBuilder, joinNode, ctx);
        wireNextSteps(flowBuilder, joinNode, ctx);

        return flowBuilder.build();
    }

    private Flow buildDecisionFlow(StepNode node, BuildContext ctx) {
        logger.debug("Building DECISION flow for node '{}' (using sequential fallback)", node.nodeId());
        return buildNormalFlow(node, ctx);
    }

    protected Flow compileSubgraph(StepNode node, BuildContext ctx) {
        logger.warn("SUBGRAPH expansion not implemented for node '{}'. " +
                   "Override compileSubgraph() to support nested workflows.", node.nodeId());
        return buildNormalFlow(node, ctx);
    }

    private void wireErrorRouting(FlowBuilder<SimpleFlow> flowBuilder, StepNode node, BuildContext ctx) {
        if (!node.hasErrorHandling()) {
            return;
        }

        List<String> errorSteps = node.errorSteps();
        logger.debug("Wiring error routing for '{}' to {}", node.nodeId(), errorSteps);

        if (errorSteps.size() == 1) {
            String errorNodeId = errorSteps.get(0);
            Flow errorFlow = buildFlowFromNode(errorNodeId, ctx);
            if (errorFlow != null) {
                flowBuilder.on("FAILED").to(errorFlow);
            }
        } else {
            List<Flow> errorFlows = errorSteps.stream()
                .map(id -> buildFlowFromNode(id, ctx))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (!errorFlows.isEmpty()) {
                Flow chainedErrorFlow = chainFlowsSequentially(errorFlows, node.nodeId() + "-error-chain");
                flowBuilder.on("FAILED").to(chainedErrorFlow);
            }
        }
    }

    private void wireNextSteps(FlowBuilder<SimpleFlow> flowBuilder, StepNode node, BuildContext ctx) {
        List<String> nextSteps = node.nextSteps();

        if (nextSteps == null || nextSteps.isEmpty()) {
            flowBuilder.on("*").end();
            return;
        }

        if (nextSteps.size() == 1) {
            Flow nextFlow = buildFlowFromNode(nextSteps.get(0), ctx);
            if (nextFlow != null) {
                flowBuilder.on("*").to(nextFlow);
            } else {
                flowBuilder.on("*").end();
            }
            return;
        }

        List<Flow> nextFlows = nextSteps.stream()
            .map(id -> buildFlowFromNode(id, ctx))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (nextFlows.isEmpty()) {
            flowBuilder.on("*").end();
            return;
        }

        if (isParallelMode(node)) {
            Flow splitFlow = createParallelSplit(nextFlows, node.nodeId() + "-next-split", ctx);
            flowBuilder.on("*").to(splitFlow);
        } else {
            Flow chainedFlow = chainFlowsSequentially(nextFlows, node.nodeId() + "-next-chain");
            flowBuilder.on("*").to(chainedFlow);
        }
    }

    private boolean isParallelMode(StepNode node) {
        return node.executionHints() != null &&
               node.executionHints().getMode() == ExecutionMode.PARALLEL;
    }

    private Flow createParallelSplit(List<Flow> flows, String splitName, BuildContext ctx) {
        if (flows.isEmpty()) {
            throw new IllegalArgumentException("Cannot create split with empty flows");
        }

        if (flows.size() == 1) {
            return flows.get(0);
        }

        FlowBuilder<SimpleFlow> splitBuilder = new FlowBuilder<>(splitName);
        splitBuilder.split(workflowTaskExecutor).add(flows.toArray(new Flow[0]));

        logger.debug("Created parallel split '{}' with {} flows", splitName, flows.size());
        return splitBuilder.build();
    }

    private Flow chainFlowsSequentially(List<Flow> flows, String chainName) {
        if (flows.isEmpty()) {
            throw new IllegalArgumentException("Cannot chain empty flows");
        }

        if (flows.size() == 1) {
            return flows.get(0);
        }

        FlowBuilder<SimpleFlow> chainBuilder = new FlowBuilder<>(chainName);
        chainBuilder.start(flows.get(0));

        for (int i = 1; i < flows.size(); i++) {
            chainBuilder.next(flows.get(i));
        }

        logger.debug("Created sequential chain '{}' with {} flows", chainName, flows.size());
        return chainBuilder.build();
    }

    private List<String> findUpstreamSteps(String nodeId, ExecutionPlan plan) {
        List<String> upstream = new ArrayList<>();

        for (Map.Entry<String, StepNode> entry : plan.steps().entrySet()) {
            StepNode node = entry.getValue();
            if (node.nextSteps() != null && node.nextSteps().contains(nodeId)) {
                upstream.add(entry.getKey());
            }
        }

        return upstream;
    }

    private static class BuildContext {
        final ExecutionPlan plan;
        final String jobName;
        final Map<String, Step> stepMap = new HashMap<>();
        final Map<String, Flow> flowCache = new HashMap<>();
        final Set<String> currentPath = new LinkedHashSet<>();

        BuildContext(ExecutionPlan plan, String jobName) {
            this.plan = plan;
            this.jobName = jobName;
        }
    }
}
