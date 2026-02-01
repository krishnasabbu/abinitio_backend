package com.workflow.engine.execution.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.graph.StepNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowDecisionDecider implements JobExecutionDecider {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowDecisionDecider.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final String nodeId;
    private final List<DecisionBranch> branches;
    private final String defaultBranch;

    public WorkflowDecisionDecider(String nodeId, List<DecisionBranch> branches, String defaultBranch) {
        this.nodeId = nodeId;
        this.branches = branches;
        this.defaultBranch = defaultBranch != null ? defaultBranch : "default";
    }

    public static WorkflowDecisionDecider fromStepNode(StepNode node) {
        JsonNode config = node.config();
        List<DecisionBranch> branches = parseBranches(config);
        String defaultBranch = config != null && config.has("defaultBranch")
            ? config.get("defaultBranch").asText()
            : "default";

        return new WorkflowDecisionDecider(node.nodeId(), branches, defaultBranch);
    }

    private static List<DecisionBranch> parseBranches(JsonNode config) {
        if (config == null || !config.has("branches")) {
            return List.of();
        }

        JsonNode branchesNode = config.get("branches");
        if (!branchesNode.isArray()) {
            return List.of();
        }

        java.util.ArrayList<DecisionBranch> result = new java.util.ArrayList<>();
        for (JsonNode branchNode : branchesNode) {
            String condition = branchNode.has("condition") ? branchNode.get("condition").asText() : "true";
            String target = branchNode.has("target") ? branchNode.get("target").asText() : "default";
            String name = branchNode.has("name") ? branchNode.get("name").asText() : target;
            result.add(new DecisionBranch(name, condition, target));
        }
        return result;
    }

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        logger.info("[DECISION] Evaluating decision node '{}' with {} branches", nodeId, branches.size());

        Map<String, Object> context = buildEvaluationContext(jobExecution, stepExecution);

        for (DecisionBranch branch : branches) {
            try {
                boolean result = evaluateCondition(branch.condition(), context);
                logger.debug("[DECISION] Branch '{}' condition '{}' evaluated to: {}",
                    branch.name(), branch.condition(), result);

                if (result) {
                    logger.info("[DECISION] Node '{}' selected branch '{}' (target='{}')",
                        nodeId, branch.name(), branch.target());
                    return new FlowExecutionStatus(branch.target());
                }
            } catch (Exception e) {
                logger.warn("[DECISION] Error evaluating condition '{}' for branch '{}': {}",
                    branch.condition(), branch.name(), e.getMessage());
            }
        }

        logger.info("[DECISION] Node '{}' using default branch '{}'", nodeId, defaultBranch);
        return new FlowExecutionStatus(defaultBranch);
    }

    private Map<String, Object> buildEvaluationContext(JobExecution jobExecution, StepExecution stepExecution) {
        Map<String, Object> ctx = new HashMap<>();

        ctx.put("jobName", jobExecution.getJobInstance().getJobName());
        ctx.put("jobId", jobExecution.getJobId());
        ctx.put("jobStatus", jobExecution.getStatus().toString());

        jobExecution.getJobParameters().getParameters().forEach((key, param) -> {
            ctx.put("param_" + key, param.getValue());
        });

        if (stepExecution != null) {
            ctx.put("stepName", stepExecution.getStepName());
            ctx.put("stepStatus", stepExecution.getStatus().toString());
            ctx.put("readCount", stepExecution.getReadCount());
            ctx.put("writeCount", stepExecution.getWriteCount());
            ctx.put("skipCount", stepExecution.getSkipCount());
            ctx.put("commitCount", stepExecution.getCommitCount());

            stepExecution.getExecutionContext().entrySet().forEach(entry -> {
                ctx.put("ctx_" + entry.getKey(), entry.getValue());
            });
        }

        jobExecution.getExecutionContext().entrySet().forEach(entry -> {
            ctx.put("job_" + entry.getKey(), entry.getValue());
        });

        return ctx;
    }

    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank() || "true".equalsIgnoreCase(condition.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(condition.trim())) {
            return false;
        }

        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setVariables(context);

            for (Map.Entry<String, Object> entry : context.entrySet()) {
                evalContext.setVariable(entry.getKey(), entry.getValue());
            }

            Expression expression = PARSER.parseExpression(condition);
            Object result = expression.getValue(evalContext);

            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            if (result instanceof Number) {
                return ((Number) result).doubleValue() != 0;
            }
            if (result instanceof String) {
                return !((String) result).isEmpty() && !"false".equalsIgnoreCase((String) result);
            }

            return result != null;
        } catch (Exception e) {
            logger.error("[DECISION] SpEL evaluation failed for '{}': {}", condition, e.getMessage());
            return false;
        }
    }

    public record DecisionBranch(String name, String condition, String target) {}
}
