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
import java.util.Set;

@Component
public class IntersectExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Intersect";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items1 = (List<Map<String, Object>>) context.getVariable("in1InputItems");
        if (items1 == null) {
            items1 = new ArrayList<>();
        }
        return new ListItemReader<>(items1);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String keyFieldsStr = config.has("keyFields") ? config.get("keyFields").asText() : "";

        List<String> keyFields = parseArray(keyFieldsStr);

        List<Map<String, Object>> items2 = (List<Map<String, Object>>) context.getVariable("in2InputItems");
        if (items2 == null) {
            items2 = new ArrayList<>();
        }

        Set<String> set2Keys = new LinkedHashMap<String, String>().keySet();
        Map<String, Boolean> set2Index = new LinkedHashMap<>();
        for (Map<String, Object> item : items2) {
            if (item != null) {
                String keyStr = buildKeyString(item, keyFields);
                set2Index.put(keyStr, true);
            }
        }

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            Set<String> emittedKeys = new LinkedHashMap<String, String>().keySet();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                String keyStr = buildKeyString(item, keyFields);

                if (set2Index.containsKey(keyStr) && !emittedKeys.contains(keyStr)) {
                    outputItems.add(item);
                    emittedKeys.add(keyStr);
                }
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("keyFields")) {
            throw new IllegalArgumentException("Intersect node requires 'keyFields' in config");
        }
    }

    private String buildKeyString(Map<String, Object> item, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append("|");
            Object val = item.get(keys.get(i));
            sb.append(val != null ? val.toString() : "null");
        }
        return sb.toString();
    }

    private List<String> parseArray(String arrayStr) {
        List<String> result = new ArrayList<>();
        if (arrayStr == null || arrayStr.trim().isEmpty()) {
            return result;
        }
        for (String item : arrayStr.split(",")) {
            result.add(item.trim());
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
