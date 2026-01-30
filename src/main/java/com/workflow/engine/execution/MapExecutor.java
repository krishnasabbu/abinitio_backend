package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Map";
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
        String mappingsStr = config.get("mappings").asText();

        Map<String, String> mappings = parseKeyValue(mappingsStr);

        return item -> {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                String sourceField = mapping.getKey();
                String targetField = mapping.getValue();

                Object value = item.get(sourceField);
                result.put(targetField, value);
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

        if (config == null || !config.has("mappings")) {
            throw new IllegalArgumentException("Map node requires 'mappings' in config");
        }

        String mappingsStr = config.get("mappings").asText();
        if (mappingsStr == null || mappingsStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Map 'mappings' cannot be empty");
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
