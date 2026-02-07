package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
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

import java.math.BigDecimal;
import java.util.*;

@Component
public class ReformatExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ReformatExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Reformat";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();

            logger.debug("Using BufferedItemReader for Reformat node {} port 'in'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "in", routingCtx.getRoutingContext().getBufferStore());
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        String operationsStr = config.has("operations") ? config.get("operations").asText() : null;

        if (operationsStr != null && !operationsStr.trim().isEmpty()) {
            return createOperationsProcessor(operationsStr);
        } else {
            return createLegacyProcessor(config);
        }
    }

    private ItemProcessor<Map<String, Object>, Map<String, Object>> createOperationsProcessor(String operationsStr) {
        List<Map<String, Object>> operations;
        try {
            operations = objectMapper.readValue(operationsStr, List.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse operations JSON: " + e.getMessage(), e);
        }

        boolean allMaps = operations.stream().allMatch(op -> "map".equals(op.get("op")));
        Set<String> projectedTargets = new LinkedHashSet<>();
        if (allMaps) {
            for (Map<String, Object> op : operations) {
                String to = (String) op.get("to");
                if (to != null) {
                    projectedTargets.add(to);
                } else {
                    String field = (String) op.get("field");
                    if (field != null) projectedTargets.add(field);
                }
            }
        }

        return item -> {
            logger.debug("Reformat processor input: {}", item);
            Map<String, Object> result = new LinkedHashMap<>(item);

            for (Map<String, Object> operation : operations) {
                String op = (String) operation.get("op");
                if (op == null) {
                    throw new IllegalArgumentException("Operation missing 'op' field");
                }

                switch (op) {
                    case "map":
                        applyMapOperation(result, operation);
                        break;
                    case "derive":
                        applyDeriveOperation(result, operation);
                        break;
                    case "cast":
                        applyCastOperation(result, operation);
                        break;
                    case "drop":
                        applyDropOperation(result, operation);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown operation: " + op);
                }
            }

            if (!projectedTargets.isEmpty()) {
                result.keySet().retainAll(projectedTargets);
            }

            logger.debug("Reformat processor output: {}", result);
            return result;
        };
    }

    private void applyMapOperation(Map<String, Object> result, Map<String, Object> operation) {
        String from = (String) operation.get("from");
        String to = (String) operation.get("to");
        String field = (String) operation.get("field");
        String expr = (String) operation.get("expr");

        if (from == null && field != null && to != null) {
            from = field;
        }

        if (from != null && to != null) {
            Object value = result.get(from);
            result.put(to, value);
            if (!from.equals(to)) {
                result.remove(from);
            }
            return;
        }

        if (field != null && expr != null) {
            if (result.containsKey(expr)) {
                result.put(field, result.get(expr));
            } else {
                try {
                    StandardEvaluationContext evalContext = new StandardEvaluationContext(result);
                    Object value = parser.parseExpression(expr).getValue(evalContext);
                    result.put(field, value);
                } catch (Exception e) {
                    logger.warn("Map expression '{}' evaluation failed for field '{}': {}", expr, field, e.getMessage());
                    result.put(field, null);
                }
            }
            return;
        }

        throw new IllegalArgumentException(
            "Map operation requires either 'from'/'to', 'field'/'to', or 'field'/'expr' fields, got: " + operation.keySet());
    }

    private void applyDeriveOperation(Map<String, Object> result, Map<String, Object> operation) {
        String field = (String) operation.get("field");
        String expr = (String) operation.get("expr");

        if (field == null || expr == null) {
            throw new IllegalArgumentException("Derive operation requires 'field' and 'expr' fields");
        }

        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext(result);
            Object value = parser.parseExpression(expr).getValue(evalContext);
            result.put(field, value);
        } catch (Exception e) {
            result.put(field, null);
        }
    }

    private void applyCastOperation(Map<String, Object> result, Map<String, Object> operation) {
        String field = (String) operation.get("field");
        String type = (String) operation.get("type");

        if (field == null || type == null) {
            throw new IllegalArgumentException("Cast operation requires 'field' and 'type' fields");
        }

        Object value = result.get(field);
        if (value == null) {
            return;
        }

        try {
            Object converted = convertValue(value, type);
            result.put(field, converted);
        } catch (Exception e) {
            // Keep original value on conversion failure
        }
    }

    private Object convertValue(Object value, String type) {
        String strValue = value.toString();
        switch (type.toLowerCase()) {
            case "int":
                return Integer.parseInt(strValue);
            case "long":
                return Long.parseLong(strValue);
            case "double":
                return Double.parseDouble(strValue);
            case "decimal":
                return new BigDecimal(strValue);
            case "string":
                return strValue;
            case "boolean":
                return Boolean.parseBoolean(strValue);
            default:
                throw new IllegalArgumentException("Unsupported cast type: " + type);
        }
    }

    private void applyDropOperation(Map<String, Object> result, Map<String, Object> operation) {
        List<String> fields = (List<String>) operation.get("fields");
        if (fields == null) {
            throw new IllegalArgumentException("Drop operation requires 'fields' array");
        }

        for (String field : fields) {
            result.remove(field);
        }
    }

    private ItemProcessor<Map<String, Object>, Map<String, Object>> createLegacyProcessor(JsonNode config) {
        String fieldMappingsStr = config.has("fieldMappings") ? config.get("fieldMappings").asText() : null;
        String dropFieldsStr = config.has("dropFields") ? config.get("dropFields").asText() : null;

        List<FieldMapping> mappings = new ArrayList<>();
        if (fieldMappingsStr != null && !fieldMappingsStr.trim().isEmpty()) {
            for (String line : fieldMappingsStr.split("\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    mappings.add(parseFieldMapping(line));
                }
            }
        }

        Set<String> dropFields = new HashSet<>();
        if (dropFieldsStr != null && !dropFieldsStr.trim().isEmpty()) {
            String[] fields = dropFieldsStr.split(",");
            for (String field : fields) {
                dropFields.add(field.trim());
            }
        }

        return item -> {
            Map<String, Object> result = new LinkedHashMap<>();

            for (FieldMapping mapping : mappings) {
                if (mapping.expression != null) {
                    try {
                        StandardEvaluationContext evalContext = new StandardEvaluationContext(item);
                        Object value = parser.parseExpression(mapping.expression).getValue(evalContext);
                        result.put(mapping.target, value);
                    } catch (Exception e) {
                        result.put(mapping.target, null);
                    }
                } else {
                    Object value = item.get(mapping.source);
                    result.put(mapping.target, value);
                }
            }

            if (mappings.isEmpty()) {
                result.putAll(item);
            }

            for (String field : dropFields) {
                result.remove(field);
            }

            return result;
        };
    }

    private FieldMapping parseFieldMapping(String line) {
        String[] parts = line.split(":", 3);
        FieldMapping mapping = new FieldMapping();
        mapping.source = parts[0].trim();
        mapping.target = parts.length > 1 ? parts[1].trim() : parts[0].trim();
        mapping.expression = parts.length > 2 ? parts[2].trim() : null;
        return mapping;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        String nodeId = context.getNodeDefinition().getId();
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("Reformat node {} writing {} items to routing context", nodeId, outputList.size());
            if (logger.isDebugEnabled() && !outputList.isEmpty()) {
                logger.debug("Reformat node {} first output record: {}", nodeId, outputList.get(0));
            }
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null) {
            throw new IllegalArgumentException("Reformat node requires config");
        }

        String operationsStr = config.has("operations") ? config.get("operations").asText() : null;

        if (operationsStr != null && !operationsStr.trim().isEmpty()) {
            try {
                objectMapper.readValue(operationsStr, List.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid operations JSON: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return true;
    }

    private static class FieldMapping {
        String source;
        String target;
        String expression;
    }
}
