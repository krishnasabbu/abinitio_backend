package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingItemWriter;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        boolean isRouting = context instanceof RoutingNodeExecutionContext;

        return item -> {
            String flagKey = "jobCondition_" + nodeId;
            boolean conditionResult;

            if (context.getVariable(flagKey) != null) {
                conditionResult = (Boolean) context.getVariable(flagKey);
            } else {
                try {
                    SimpleEvaluationContext evalCtx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                    Object result = parser.parseExpression(expression).getValue(evalCtx, context.getVariables());
                    conditionResult = result instanceof Boolean && (Boolean) result;
                    context.setVariable(flagKey, conditionResult);
                    logger.debug("nodeId={}, JobCondition evaluated to {}", nodeId, conditionResult);
                } catch (Exception e) {
                    logger.error("nodeId={}, JobCondition evaluation failed: {}", nodeId, e.getMessage());
                    throw new RuntimeException("nodeType=JobCondition, nodeId=" + nodeId + ", evaluation failed: " + e.getMessage());
                }
            }

            if (isRouting) {
                Map<String, Object> routedItem = new LinkedHashMap<>(item);
                routedItem.put("_routePort", conditionResult ? "true" : "false");
                return routedItem;
            }
            return item;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            RoutingItemWriter routingWriter = new RoutingItemWriter(routingCtx.getRoutingContext(), "_routePort");
            return items -> {
                logger.info("nodeId={}, JobCondition routing {} items", context.getNodeDefinition().getId(), items.size());
                routingWriter.write(items);
            };
        }
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, JobCondition writing {} items", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
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
