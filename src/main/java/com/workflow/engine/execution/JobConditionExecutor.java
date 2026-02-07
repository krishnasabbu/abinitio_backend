package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor for job-level condition nodes that evaluate SpEL expressions to control workflow branching.
 */
@Component
public class JobConditionExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(JobConditionExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "JobCondition";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            logger.debug("nodeId={}, Using BufferedItemReader for port 'in'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "in", routingCtx.getRoutingContext().getBufferStore());
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String expression = config.has("expression") ? config.get("expression").asText() : "";

        if (expression.isEmpty()) {
            throw new IllegalArgumentException("nodeType=JobCondition, nodeId=" + nodeId + ", missing expression");
        }

        return item -> {
            String flagKey = "jobCondition_" + nodeId;
            if (context.getVariable(flagKey) == null) {
                try {
                    StandardEvaluationContext evalCtx = new StandardEvaluationContext(context.getVariables());
                    Object result = parser.parseExpression(expression).getValue(evalCtx);
                    boolean conditionResult = result instanceof Boolean && (Boolean) result;
                    context.setVariable(flagKey, conditionResult);
                    logger.debug("JobCondition node {} evaluated to {}", nodeId, conditionResult);
                } catch (Exception e) {
                    logger.error("JobCondition evaluation failed: {}", e.getMessage());
                    throw new RuntimeException("nodeType=JobCondition, nodeId=" + nodeId + ", evaluation failed: " + e.getMessage());
                }
            }
            return item;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            logger.info("JobConditionExecutor writing {} items", items.getItems().size());
            context.setVariable("outputItems", new ArrayList<>(items.getItems()));
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("expression")) {
            throw new IllegalArgumentException("nodeType=JobCondition, nodeId=" + context.getNodeDefinition().getId()
                + ", missing expression");
        }
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return false;
    }
}
