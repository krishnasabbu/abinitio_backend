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
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for computing derived fields using SpEL expressions.
 *
 * Evaluates SpEL expressions for each item to compute new field values or transform
 * existing fields. Expressions have access to all fields in the current item.
 *
 * Configuration:
 * - expressions: (required) Multi-line string of fieldName: expression pairs
 *                Each line contains one expression in format "fieldName: spel_expression"
 *                Example: "total: price * quantity\nfull_name: firstName + ' ' + lastName"
 *
 * Output:
 * Sets "outputItems" variable with items enhanced with computed fields.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class ComputeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ComputeExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Compute";
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
        logger.debug("nodeId={}, Creating compute reader", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            logger.debug("nodeId={}, No input items", context.getNodeDefinition().getId());
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating compute processor", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();
        String expressionsStr = config.get("expressions").asText();

        Map<String, String> expressions = parseKeyValue(expressionsStr);
        logger.debug("nodeId={}, Parsed {} computed field expressions", context.getNodeDefinition().getId(), expressions.size());

        return item -> {
            Map<String, Object> result = new LinkedHashMap<>(item);

            for (Map.Entry<String, String> expr : expressions.entrySet()) {
                String fieldName = expr.getKey();
                String expression = expr.getValue();

                try {
                    logger.debug("nodeId={}, Evaluating expression for field: {}", context.getNodeDefinition().getId(), fieldName);
                    SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadWriteDataBinding().build();
                    Object value = parser.parseExpression(expression).getValue(evalContext, result);
                    result.put(fieldName, value);
                } catch (Exception e) {
                    logger.error("nodeId={}, Failed to evaluate expression for field '{}': {}", context.getNodeDefinition().getId(), fieldName, e.getMessage(), e);
                    throw new IllegalArgumentException("Failed to evaluate expression for field '" + fieldName + "': " + e.getMessage(), e);
                }
            }

            return result;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating compute writer", context.getNodeDefinition().getId());
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, Compute output: {} items processed with computed fields", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating Compute configuration", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("expressions")) {
            logger.error("nodeId={}, Configuration invalid - missing 'expressions' property", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Compute node requires 'expressions' in config");
        }

        String expressionsStr = config.get("expressions").asText();
        if (expressionsStr == null || expressionsStr.trim().isEmpty()) {
            logger.error("nodeId={}, Configuration invalid - 'expressions' cannot be empty", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Compute 'expressions' cannot be empty");
        }
        logger.debug("nodeId={}, Compute configuration valid", context.getNodeDefinition().getId());
    }

    /**
     * Parses key-value pairs from newline-separated string.
     *
     * @param keyValueStr string containing key: value pairs separated by newlines
     * @return map of parsed key-value pairs
     */
    private Map<String, String> parseKeyValue(String keyValueStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (keyValueStr == null || keyValueStr.trim().isEmpty()) {
            return result;
        }

        for (String line : keyValueStr.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String key = line.substring(0, colonIdx).trim();
                    String value = line.substring(colonIdx + 1).trim();
                    result.put(key, value);
                }
            }
        }

        return result;
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return true;
    }
}
