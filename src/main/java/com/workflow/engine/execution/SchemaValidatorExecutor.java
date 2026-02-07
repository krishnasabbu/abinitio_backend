package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executor for the SchemaValidator node type, which validates input items against a defined schema and separates valid from invalid items.
 */
@Component
public class SchemaValidatorExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidatorExecutor.class);

    @Override
    public String getNodeType() {
        return "SchemaValidator";
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
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String schemaFieldsStr = config.has("schemaFields") ? config.get("schemaFields").asText() : "";
        String onMismatch = config.has("onMismatch") ? config.get("onMismatch").asText() : "FAIL";
        String nodeId = context.getNodeDefinition().getId();

        Map<String, String> expectedFields = parseSchemaFields(schemaFieldsStr);
        Set<String> expectedFieldNames = expectedFields.keySet();
        boolean isRouting = context instanceof RoutingNodeExecutionContext;

        return items -> {
            List<Map<String, Object>> validItems = new ArrayList<>();
            List<Map<String, Object>> invalidItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                Set<String> itemFields = item.keySet();
                boolean isValid = true;

                if (onMismatch.equals("FAIL")) {
                    if (!itemFields.equals(expectedFieldNames)) {
                        isValid = false;
                    }
                } else if (onMismatch.equals("WARN")) {
                    isValid = true;
                } else if (onMismatch.equals("AUTO_MAP")) {
                    Map<String, Object> mappedItem = new LinkedHashMap<>();
                    for (String field : expectedFieldNames) {
                        mappedItem.put(field, item.get(field));
                    }
                    item = mappedItem;
                    isValid = true;
                }

                if (isValid) {
                    validItems.add(item);
                } else {
                    Map<String, Object> invalidItem = new LinkedHashMap<>(item);
                    invalidItem.put("_schema_error", "Field mismatch: expected " + expectedFieldNames + ", got " + itemFields);
                    invalidItems.add(invalidItem);
                }
            }

            logger.info("nodeId={}, SchemaValidator produced {} valid and {} invalid items", nodeId, validItems.size(), invalidItems.size());

            if (isRouting) {
                List<Map<String, Object>> allRoutedItems = new ArrayList<>();
                for (Map<String, Object> validItem : validItems) {
                    Map<String, Object> annotated = new LinkedHashMap<>(validItem);
                    annotated.put("_routePort", "out");
                    allRoutedItems.add(annotated);
                }
                for (Map<String, Object> invalidItem : invalidItems) {
                    Map<String, Object> annotated = new LinkedHashMap<>(invalidItem);
                    annotated.put("_routePort", "invalid");
                    allRoutedItems.add(annotated);
                }
                context.setVariable("outputItems", allRoutedItems);
            } else {
                context.setVariable("outputItems", validItems);
                context.setVariable("invalidItems", invalidItems);
            }
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("schemaFields")) {
            throw new IllegalArgumentException("SchemaValidator node requires 'schemaFields' in config");
        }
    }

    private Map<String, String> parseSchemaFields(String schemaStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (schemaStr == null || schemaStr.trim().isEmpty()) {
            return result;
        }

        for (String line : schemaStr.split(",")) {
            line = line.trim();
            if (!line.isEmpty()) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String fieldName = line.substring(0, colonIdx).trim();
                    String fieldType = line.substring(colonIdx + 1).trim();
                    result.put(fieldName, fieldType);
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
