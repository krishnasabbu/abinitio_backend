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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor for wait nodes that pause workflow execution based on time, schedule, or condition.
 */
@Component
public class WaitExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(WaitExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Wait";
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
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(
            NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();

        String waitType = config.has("waitType") ? config.get("waitType").asText() : "TIME";

        return item -> {
            String flagKey = "wait_executed_" + nodeId;
            Object alreadyRun = context.getVariable(flagKey);

            if (alreadyRun == null) {
                performWait(waitType, config, context, nodeId);
                context.setVariable(flagKey, true);
                logger.debug("Wait node {} completed wait action", nodeId);
            }

            return item;
        };
    }

    private void performWait(String waitType, JsonNode config, NodeExecutionContext context, String nodeId) {
        if ("TIME".equals(waitType)) {
            long durationSeconds = config.has("durationSeconds")
                ? config.get("durationSeconds").asLong()
                : 300;
            try {
                logger.info("Wait node {} sleeping for {} seconds", nodeId, durationSeconds);
                Thread.sleep(durationSeconds * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait interrupted", e);
            }
        } else if ("UNTIL".equals(waitType)) {
            String untilExpression = config.has("untilExpression")
                ? config.get("untilExpression").asText()
                : "";
            if (untilExpression.isEmpty()) {
                throw new IllegalArgumentException("nodeType=Wait, nodeId=" + nodeId + ", missing untilExpression for UNTIL type");
            }
            waitUntil(untilExpression, nodeId);
        } else if ("CONDITION".equals(waitType)) {
            String condition = config.has("condition")
                ? config.get("condition").asText()
                : "";
            if (condition.isEmpty()) {
                throw new IllegalArgumentException("nodeType=Wait, nodeId=" + nodeId + ", missing condition for CONDITION type");
            }
            waitForCondition(condition, context, nodeId);
        }
    }

    private void waitUntil(String untilExpression, String nodeId) {
        long endTime = parseUntilExpression(untilExpression);
        long now;
        while ((now = System.currentTimeMillis()) < endTime) {
            try {
                long sleepMs = Math.min(1000, endTime - now);
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait UNTIL interrupted", e);
            }
        }
        logger.info("Wait node {} until condition reached", nodeId);
    }

    private long parseUntilExpression(String expr) {
        try {
            if (expr.matches("\\d{2}:\\d{2}")) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime target = LocalDateTime.parse(now.toLocalDate() + "T" + expr + ":00",
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                if (target.isBefore(now)) {
                    target = target.plusDays(1);
                }
                return target.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else {
                LocalDateTime target = LocalDateTime.parse(expr);
                return target.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid until expression: " + expr, e);
        }
    }

    private void waitForCondition(String condition, NodeExecutionContext context, String nodeId) {
        long maxWaitMs = 300000;
        long startMs = System.currentTimeMillis();

        while (System.currentTimeMillis() - startMs < maxWaitMs) {
            try {
                StandardEvaluationContext evalCtx = new StandardEvaluationContext(context.getVariables());
                Object result = parser.parseExpression(condition).getValue(evalCtx);
                if (result instanceof Boolean && (Boolean) result) {
                    logger.info("Wait node {} condition satisfied", nodeId);
                    return;
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait CONDITION interrupted", e);
            }
        }
        throw new RuntimeException("Wait condition not satisfied within timeout");
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            logger.info("WaitExecutor writing {} items", items.getItems().size());
            context.setVariable("outputItems", new ArrayList<>(items.getItems()));
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=Wait, nodeId=" + context.getNodeDefinition().getId()
                + ", missing config object");
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
