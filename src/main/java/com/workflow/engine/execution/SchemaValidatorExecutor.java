package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
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

@Component
public class SchemaValidatorExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "SchemaValidator";
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
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String schemaFieldsStr = config.has("schemaFields") ? config.get("schemaFields").asText() : "";
        String onMismatch = config.has("onMismatch") ? config.get("onMismatch").asText() : "FAIL";

        Map<String, String> expectedFields = parseSchemaFields(schemaFieldsStr);
        Set<String> expectedFieldNames = expectedFields.keySet();

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

            context.setVariable("outputItems", validItems);
            context.setVariable("invalidItems", invalidItems);
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
