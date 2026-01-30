package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SortExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Sort";
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
        String sortKeysStr = config.has("sortKeys") ? config.get("sortKeys").asText() : "";

        Map<String, String> sortKeys = parseSortKeys(sortKeysStr);

        return items -> {
            List<Map<String, Object>> allItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    allItems.add(item);
                }
            }

            allItems.sort((a, b) -> {
                for (Map.Entry<String, String> sortSpec : sortKeys.entrySet()) {
                    String field = sortSpec.getKey();
                    String direction = sortSpec.getValue().toLowerCase();

                    Object aVal = a.get(field);
                    Object bVal = b.get(field);

                    int comparison = compareObjects(aVal, bVal);
                    if (comparison != 0) {
                        return direction.equals("desc") ? -comparison : comparison;
                    }
                }
                return 0;
            });

            context.setVariable("outputItems", allItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("sortKeys")) {
            throw new IllegalArgumentException("Sort node requires 'sortKeys' in config");
        }
    }

    private int compareObjects(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b);
            } catch (Exception e) {
                return a.toString().compareTo(b.toString());
            }
        }

        return a.toString().compareTo(b.toString());
    }

    private Map<String, String> parseSortKeys(String sortKeysStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (sortKeysStr == null || sortKeysStr.trim().isEmpty()) {
            return result;
        }

        for (String line : sortKeysStr.split("\n")) {
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
