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
 * FORK Pattern (EXPLICIT JOIN REQUIRED):
 *   [StepA] (kind=FORK, executionHints.joinNodeId="join-step")
 *      |
 *   split(executor)
 *    / | \
 * [B] [C] [D]  <- parallel branches
 *    \ | /
 *    (join)    <- split completes when ALL branches done
 *      |
 *   [join-step] (kind=JOIN) <- explicit JOIN node executes ONCE
 *      |
 *   [Continue]
 * </pre>
 *
 * <h2>Key Design Decisions</h2>
 * <ul>
 *   <li>JOIN is explicit: Fork nodes MUST declare joinNodeId in executionHints</li>
 *   <li>No implicit join inference: Forks without explicit join fail fast</li>
 *   <li>Error routing: FAILED/STOPPED/UNKNOWN route to errorSteps, then end</li>
 *   <li>Deterministic naming: workflowId is REQUIRED (no UUID fallback in prod)</li>
 *   <li>Shared executor: Uses DI'd ThreadPoolTaskExecutor with MDC propagation</li>
 *   <li>DECISION/SUBGRAPH: Fail fast until properly implemented</li>
 * </ul>
 *
 * @see ExecutionPlan
 * @see ExecutionPlanValidator
 * @see JoinBarrierTasklet
 */
@Component
public class DynamicJobBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DynamicJobBuilder.class);

    private static final Set<String> ERROR_STATUSES = Set.of("FAILED", "STOPPED", "UNKNOWN");

    private final StepFactory stepFactory;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TaskExecutor workflowTaskExecutor;

    @Value("${workflow.job.restartable:true}")
    private boolean restartable;

    @Value("${workflow.job.require-workflow-id:true}")
    private boolean requireWorkflowId;

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
        return buildJob(plan, plan.workflowId());
    }

    public Job buildJob(ExecutionPlan plan, String workflowId) {
        Objects.requireNonNull(plan, "ExecutionPlan cannot be null");

        ExecutionPlanValidator.validate(plan);

        String effectiveWorkflowId = workflowId != null ? workflowId : plan.workflowId();
        String jobName = determineJobName(effectiveWorkflowId);
        logger.info("Building job '{}' with {} steps, {} entry points",
            jobName, plan.steps().size(), plan.entryStepIds().size());

        BuildContext ctx = new BuildContext(plan, jobName);

        validateForkJoinStructure(ctx);
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

    private String determineJobName(String workflowId) {
        if (workflowId != null && !workflowId.isBlank()) {
            return "workflow-" + workflowId.trim();
        }

        if (requireWorkflowId) {
            throw new IllegalArgumentException(
                "workflowId is required for job building. " +
                "Set workflow.job.require-workflow-id=false to allow UUID fallback.");
        }

        String uuid = UUID.randomUUID().toString();
        logger.warn("No workflowId provided, using UUID: {}. This breaks restart semantics.", uuid);
        return "workflow-" + uuid;
    }

    private void validateForkJoinStructure(BuildContext ctx) {
        for (Map.Entry<String, StepNode> entry : ctx.plan.steps().entrySet()) {
            String nodeId = entry.getKey();
            StepNode node = entry.getValue();

            if (node.isFork()) {
                validateForkNode(nodeId, node, ctx);
            }

            if (node.isJoin()) {
                validateJoinNode(nodeId, node, ctx);
            }
        }
    }

    private void validateForkNode(String nodeId, StepNode node, BuildContext ctx) {
        List<String> nextSteps = node.nextSteps();
        if (nextSteps == null || nextSteps.size() <= 1) {
            return;
        }

        String joinNodeId = getExplicitJoinNodeId(node);
        if (joinNodeId == null) {
            throw new IllegalStateException(String.format(
                "FORK node '%s' has %d branches but no explicit joinNodeId. " +
                "Set executionHints.joinNodeId to the JOIN node where branches converge.",
                nodeId, nextSteps.size()));
        }

        StepNode joinNode = ctx.plan.steps().get(joinNodeId);
        if (joinNode == null) {
            throw new IllegalStateException(String.format(
                "FORK node '%s' references joinNodeId '%s' which does not exist.",
                nodeId, joinNodeId));
        }

        if (!joinNode.isJoin()) {
            throw new IllegalStateException(String.format(
                "FORK node '%s' references joinNodeId '%s' but that node has kind=%s, not JOIN. " +
                "The target node must have kind=JOIN.",
                nodeId, joinNodeId, joinNode.kind()));
        }

        for (String branchStart : nextSteps) {
            if (!canReachNode(branchStart, joinNodeId, ctx, new HashSet<>())) {
                throw new IllegalStateException(String.format(
                    "FORK node '%s' branch '%s' cannot reach join node '%s'. " +
                    "All branches must converge at the declared join.",
                    nodeId, branchStart, joinNodeId));
            }
        }

        logger.debug("Validated FORK '{}' -> JOIN '{}'", nodeId, joinNodeId);
    }

    private void validateJoinNode(String nodeId, StepNode node, BuildContext ctx) {
        List<String> upstreamSteps = findUpstreamSteps(nodeId, ctx.plan);
        if (upstreamSteps.size() < 2) {
            logger.warn("JOIN node '{}' has only {} upstream step(s). " +
                       "JOIN nodes typically synchronize multiple branches.",
                nodeId, upstreamSteps.size());
        }
    }

    private boolean canReachNode(String fromId, String targetId, BuildContext ctx, Set<String> visited) {
        if (fromId.equals(targetId)) {
            return true;
        }
        if (visited.contains(fromId)) {
            return false;
        }
        visited.add(fromId);

        StepNode node = ctx.plan.steps().get(fromId);
        if (node == null || node.nextSteps() == null) {
            return false;
        }

        for (String nextId : node.nextSteps()) {
            if (canReachNode(nextId, targetId, ctx, visited)) {
                return true;
            }
        }
        return false;
    }

    private String getExplicitJoinNodeId(StepNode forkNode) {
        if (forkNode.executionHints() != null &&
            forkNode.executionHints().getJoinNodeId() != null) {
            return forkNode.executionHints().getJoinNodeId();
        }
        return null;
    }

    private void buildAllSteps(BuildContext ctx) {
        for (Map.Entry<String, StepNode> entry : ctx.plan.steps().entrySet()) {
            String nodeId = entry.getKey();
            StepNode node = entry.getValue();

            Step step;
            if (isBarrierOnly(node)) {
                step = buildBarrierStep(ctx, node);
            } else {
                step = stepFactory.buildStep(node);
            }

            ctx.stepMap.put(nodeId, step);
            logger.debug("Built step for node '{}' (kind={})", nodeId, node.kind());
        }
    }

    private boolean isBarrierOnly(StepNode node) {
        return node.kind() == StepKind.BARRIER ||
               (node.kind() == StepKind.JOIN && isBarrierNodeType(node));
    }

    private boolean isBarrierNodeType(StepNode node) {
        String nodeType = node.nodeType();
        return "Barrier".equals(nodeType) ||
               "JoinBarrier".equals(nodeType) ||
               "Collect".equals(nodeType);
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
            return buildFlowFromNode(entryStepIds.get(0), ctx, FlowMode.NORMAL);
        }

        List<Flow> entryFlows = entryStepIds.stream()
            .map(id -> buildFlowFromNode(id, ctx, FlowMode.NORMAL))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (entryFlows.isEmpty()) {
            throw new IllegalStateException("No valid entry flows could be built");
        }

        return createParallelSplit(entryFlows, "main-split");
    }

    private Flow buildFlowFromNode(String nodeId, BuildContext ctx, FlowMode mode) {
        String cacheKey = nodeId + ":" + mode;
        if (ctx.flowCache.containsKey(cacheKey)) {
            logger.debug("Returning cached flow for node '{}' mode={}", nodeId, mode);
            return ctx.flowCache.get(cacheKey);
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
            if (node.isSubgraph()) {
                throw new UnsupportedOperationException(String.format(
                    "SUBGRAPH node '%s' is not yet supported. " +
                    "Subgraph expansion requires explicit implementation.", nodeId));
            } else if (node.isDecision()) {
                throw new UnsupportedOperationException(String.format(
                    "DECISION node '%s' is not yet supported. " +
                    "Decision routing requires JobExecutionDecider implementation.", nodeId));
            } else if (node.isJoin()) {
                flow = buildJoinFlow(node, ctx);
            } else if (node.isFork()) {
                flow = buildForkFlow(node, ctx);
            } else {
                flow = buildNormalFlow(node, ctx);
            }

            ctx.flowCache.put(cacheKey, flow);
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

        if (nextSteps.size() == 1) {
            Flow nextFlow = buildFlowFromNode(nextSteps.get(0), ctx, FlowMode.NORMAL);
            flowBuilder.on("*").to(nextFlow);
            return flowBuilder.build();
        }

        String joinNodeId = getExplicitJoinNodeId(node);
        if (joinNodeId == null) {
            throw new IllegalStateException(
                "FORK node '" + node.nodeId() + "' has multiple branches but no joinNodeId");
        }

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

        Flow splitFlow = createParallelSplit(branchFlows, node.nodeId() + "-split");
        flowBuilder.on("*").to(splitFlow);

        Flow joinContinuation = buildFlowFromNode(joinNodeId, ctx, FlowMode.NORMAL);
        return new FlowBuilder<SimpleFlow>(node.nodeId() + "-fork-join")
            .start(flowBuilder.build())
            .next(joinContinuation)
            .build();
    }

    private Flow buildBranchUntilJoin(String startNodeId, String joinNodeId, BuildContext ctx) {
        if (startNodeId.equals(joinNodeId)) {
            return null;
        }

        StepNode node = ctx.plan.steps().get(startNodeId);
        if (node == null) {
            throw new IllegalStateException("Branch node not found: " + startNodeId);
        }

        if (node.isJoin() && !startNodeId.equals(joinNodeId)) {
            throw new IllegalStateException(String.format(
                "Branch encountered unexpected JOIN node '%s' before target join '%s'. " +
                "Nested joins are not supported in branch compilation.",
                startNodeId, joinNodeId));
        }

        if (node.isSubgraph()) {
            throw new UnsupportedOperationException(String.format(
                "SUBGRAPH node '%s' encountered in branch. Subgraph expansion not implemented.",
                startNodeId));
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
            if (node.isFork()) {
                String nestedJoinId = getExplicitJoinNodeId(node);
                if (nestedJoinId == null) {
                    throw new IllegalStateException(String.format(
                        "Nested FORK '%s' within branch has no joinNodeId", startNodeId));
                }

                List<Flow> subBranches = new ArrayList<>();
                for (String nextId : nextSteps) {
                    Flow subBranch = buildBranchUntilJoin(nextId, nestedJoinId, ctx);
                    if (subBranch != null) {
                        subBranches.add(subBranch);
                    }
                }

                if (!subBranches.isEmpty()) {
                    Flow nestedSplit = createParallelSplit(subBranches, startNodeId + "-nested-split");
                    flowBuilder.on("*").to(nestedSplit);

                    Flow afterNestedJoin = buildBranchUntilJoin(nestedJoinId, joinNodeId, ctx);
                    if (afterNestedJoin != null) {
                        return new FlowBuilder<SimpleFlow>(startNodeId + "-nested-fork-join")
                            .start(flowBuilder.build())
                            .next(buildFlowFromNode(nestedJoinId, ctx, FlowMode.BRANCH))
                            .next(afterNestedJoin)
                            .build();
                    }
                }
            } else {
                List<Flow> nextFlows = new ArrayList<>();
                for (String nextId : nextSteps) {
                    if (!nextId.equals(joinNodeId)) {
                        Flow nextFlow = buildBranchUntilJoin(nextId, joinNodeId, ctx);
                        if (nextFlow != null) {
                            nextFlows.add(nextFlow);
                        }
                    }
                }

                if (!nextFlows.isEmpty()) {
                    Flow chainedFlow = chainFlowsSequentially(nextFlows, startNodeId + "-chain");
                    flowBuilder.on("*").to(chainedFlow);
                } else {
                    flowBuilder.on("*").end();
                }
            }
        }

        return flowBuilder.build();
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

    private void wireErrorRouting(FlowBuilder<SimpleFlow> flowBuilder, StepNode node, BuildContext ctx) {
        if (!node.hasErrorHandling()) {
            return;
        }

        List<String> errorSteps = node.errorSteps();
        logger.debug("Wiring error routing for '{}' to {}", node.nodeId(), errorSteps);

        Flow errorFlow;
        if (errorSteps.size() == 1) {
            errorFlow = buildFlowFromNode(errorSteps.get(0), ctx, FlowMode.ERROR);
        } else {
            List<Flow> errorFlows = errorSteps.stream()
                .map(id -> buildFlowFromNode(id, ctx, FlowMode.ERROR))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (errorFlows.isEmpty()) {
                return;
            }
            errorFlow = chainFlowsSequentially(errorFlows, node.nodeId() + "-error-chain");
        }

        Flow errorFlowThenEnd = new FlowBuilder<SimpleFlow>(node.nodeId() + "-error-end")
            .start(errorFlow)
            .on("*").end()
            .build();

        for (String errorStatus : ERROR_STATUSES) {
            flowBuilder.on(errorStatus).to(errorFlowThenEnd);
        }
    }

    private void wireNextSteps(FlowBuilder<SimpleFlow> flowBuilder, StepNode node, BuildContext ctx) {
        List<String> nextSteps = node.nextSteps();

        if (nextSteps == null || nextSteps.isEmpty()) {
            flowBuilder.on("*").end();
            return;
        }

        if (nextSteps.size() == 1) {
            Flow nextFlow = buildFlowFromNode(nextSteps.get(0), ctx, FlowMode.NORMAL);
            if (nextFlow != null) {
                flowBuilder.on("*").to(nextFlow);
            } else {
                flowBuilder.on("*").end();
            }
            return;
        }

        List<Flow> nextFlows = nextSteps.stream()
            .map(id -> buildFlowFromNode(id, ctx, FlowMode.NORMAL))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (nextFlows.isEmpty()) {
            flowBuilder.on("*").end();
            return;
        }

        if (isParallelMode(node)) {
            Flow splitFlow = createParallelSplit(nextFlows, node.nodeId() + "-next-split");
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

    private Flow createParallelSplit(List<Flow> flows, String splitName) {
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

    private enum FlowMode {
        NORMAL,
        BRANCH,
        ERROR
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
