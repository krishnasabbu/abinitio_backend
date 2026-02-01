package com.workflow.engine.execution.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight synchronization barrier for JOIN nodes in the workflow graph.
 *
 * JoinBarrierTasklet acts as the convergence point after parallel branches complete.
 * When used with Spring Batch's split().add(flows...), all flows in the split must
 * complete before execution continues past the split. This tasklet executes AFTER
 * the split completes, providing:
 *
 * 1. Explicit logging of join completion for observability
 * 2. Optional recording of upstream branch completion status
 * 3. A clear synchronization point in the job execution graph
 *
 * Fork/Join Pattern:
 * <pre>
 *   [Fork Step]
 *       |
 *    split()
 *    /    \
 * [A]     [B]   <- parallel execution
 *    \    /
 *     join     <- split completes when ALL branches done
 *       |
 * [JoinBarrier] <- this tasklet executes once after join
 *       |
 *   [Continue]
 * </pre>
 *
 * Thread safety: Thread-safe. Can be reused across job executions.
 *
 * @see DynamicJobBuilder
 */
public class JoinBarrierTasklet implements Tasklet {

    private static final Logger logger = LoggerFactory.getLogger(JoinBarrierTasklet.class);

    private final String joinNodeId;
    private final List<String> upstreamBranches;
    private final Map<String, BranchStatus> branchCompletionStatus;

    public JoinBarrierTasklet(String joinNodeId, List<String> upstreamBranches) {
        this.joinNodeId = joinNodeId;
        this.upstreamBranches = upstreamBranches != null ? upstreamBranches : List.of();
        this.branchCompletionStatus = new ConcurrentHashMap<>();
    }

    public JoinBarrierTasklet(String joinNodeId) {
        this(joinNodeId, null);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long startTime = System.currentTimeMillis();

        String jobName = chunkContext.getStepContext().getJobName();
        Long jobInstanceId = chunkContext.getStepContext().getStepExecution()
            .getJobExecution().getJobInstance().getInstanceId();
        Long jobExecutionId = chunkContext.getStepContext().getStepExecution()
            .getJobExecution().getId();

        MDC.put("joinNodeId", joinNodeId);
        MDC.put("jobExecutionId", String.valueOf(jobExecutionId));

        try {
            logger.info("[GRAPH] JOIN barrier '{}' fired - all {} upstream branches completed. " +
                       "Job: {} (instance={}, execution={})",
                joinNodeId, upstreamBranches.size(), jobName, jobInstanceId, jobExecutionId);

            if (!upstreamBranches.isEmpty()) {
                logger.info("[GRAPH] JOIN '{}' synchronized branches: {}", joinNodeId, upstreamBranches);
            }

            contribution.incrementWriteCount(1);

            storeJoinMetadata(chunkContext, startTime);

            logger.debug("[GRAPH] JOIN barrier '{}' completed in {}ms",
                joinNodeId, System.currentTimeMillis() - startTime);

            return RepeatStatus.FINISHED;
        } finally {
            MDC.remove("joinNodeId");
            MDC.remove("jobExecutionId");
        }
    }

    private void storeJoinMetadata(ChunkContext chunkContext, long startTime) {
        var executionContext = chunkContext.getStepContext()
            .getStepExecution().getJobExecution().getExecutionContext();

        String metadataKey = "join." + joinNodeId;
        Map<String, Object> joinMetadata = Map.of(
            "joinNodeId", joinNodeId,
            "upstreamBranches", upstreamBranches,
            "completionTime", System.currentTimeMillis(),
            "durationMs", System.currentTimeMillis() - startTime
        );

        executionContext.put(metadataKey, joinMetadata);
    }

    public void recordBranchCompletion(String branchId, boolean success) {
        branchCompletionStatus.put(branchId, new BranchStatus(
            branchId, success, System.currentTimeMillis()));
        logger.debug("JOIN '{}' recorded branch '{}' completion: success={}",
            joinNodeId, branchId, success);
    }

    public boolean allBranchesComplete() {
        if (upstreamBranches.isEmpty()) {
            return true;
        }
        return branchCompletionStatus.keySet().containsAll(upstreamBranches);
    }

    public Map<String, BranchStatus> getBranchStatus() {
        return Map.copyOf(branchCompletionStatus);
    }

    public String getJoinNodeId() {
        return joinNodeId;
    }

    public List<String> getUpstreamBranches() {
        return List.copyOf(upstreamBranches);
    }

    public record BranchStatus(
        String branchId,
        boolean success,
        long completionTime
    ) {}

    public static class JoinBarrierException extends RuntimeException {
        private final String joinNodeId;
        private final List<String> failedBranches;

        public JoinBarrierException(String joinNodeId, List<String> failedBranches) {
            super(String.format("JOIN '%s' failed: branches %s did not complete successfully",
                joinNodeId, failedBranches));
            this.joinNodeId = joinNodeId;
            this.failedBranches = failedBranches;
        }

        public String getJoinNodeId() {
            return joinNodeId;
        }

        public List<String> getFailedBranches() {
            return List.copyOf(failedBranches);
        }
    }
}
