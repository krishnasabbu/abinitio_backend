package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ComputeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Compute";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String expressionsStr = config.get("expressions").asText();

        Map<String, String> expressions = parseKeyValue(expressionsStr);

        return item -> {
            Map<String, Object> result = new LinkedHashMap<>(item);

            for (Map.Entry<String, String> expr : expressions.entrySet()) {
                String fieldName = expr.getKey();
                String expression = expr.getValue();

                try {
                    StandardEvaluationContext evalContext = new StandardEvaluationContext(result);
                    Object value = parser.parseExpression(expression).getValue(evalContext);
                    result.put(fieldName, value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to evaluate expression for field '" + fieldName + "': " + e.getMessage(), e);
                }
            }

            return result;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("expressions")) {
            throw new IllegalArgumentException("Compute node requires 'expressions' in config");
        }

        String expressionsStr = config.get("expressions").asText();
        if (expressionsStr == null || expressionsStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Compute 'expressions' cannot be empty");
        }
    }

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
