package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class AggregateExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Aggregate";
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
        String groupByFieldsStr = config.has("groupByFields") ? config.get("groupByFields").asText() : "";
        String aggregatesStr = config.has("aggregates") ? config.get("aggregates").asText() : "";

        List<String> groupByFields = parseArray(groupByFieldsStr);
        Map<String, String> aggregates = parseKeyValue(aggregatesStr);

        return items -> {
            Map<String, AggregateGroup> groups = new TreeMap<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                StringBuilder keyBuilder = new StringBuilder();
                for (int i = 0; i < groupByFields.size(); i++) {
                    if (i > 0) keyBuilder.append("|");
                    Object keyValue = item.get(groupByFields.get(i));
                    keyBuilder.append(keyValue != null ? keyValue.toString() : "null");
                }

                String groupKey = keyBuilder.toString();

                if (!groups.containsKey(groupKey)) {
                    AggregateGroup group = new AggregateGroup();
                    for (String field : groupByFields) {
                        group.groupValues.put(field, item.get(field));
                    }
                    groups.put(groupKey, group);
                }

                AggregateGroup group = groups.get(groupKey);
                group.items.add(item);
            }

            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (AggregateGroup group : groups.values()) {
                Map<String, Object> result = new LinkedHashMap<>(group.groupValues);

                for (Map.Entry<String, String> agg : aggregates.entrySet()) {
                    String field = agg.getKey();
                    String function = agg.getValue().toLowerCase();

                    Object value = computeAggregate(group.items, field, function);
                    result.put(field, value);
                }

                outputItems.add(result);
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("groupByFields")) {
            throw new IllegalArgumentException("Aggregate node requires 'groupByFields' in config");
        }
        if (!config.has("aggregates")) {
            throw new IllegalArgumentException("Aggregate node requires 'aggregates' in config");
        }
    }

    private Object computeAggregate(List<Map<String, Object>> items, String field, String function) {
        switch (function) {
            case "count":
                return items.size();
            case "sum":
                return items.stream()
                    .map(m -> m.get(field))
                    .filter(v -> v != null)
                    .mapToDouble(v -> toDouble(v))
                    .sum();
            case "avg":
                return items.stream()
                    .map(m -> m.get(field))
                    .filter(v -> v != null)
                    .mapToDouble(v -> toDouble(v))
                    .average()
                    .orElse(0);
            case "min":
                return items.stream()
                    .map(m -> m.get(field))
                    .filter(v -> v != null)
                    .mapToDouble(v -> toDouble(v))
                    .min()
                    .orElse(Double.NaN);
            case "max":
                return items.stream()
                    .map(m -> m.get(field))
                    .filter(v -> v != null)
                    .mapToDouble(v -> toDouble(v))
                    .max()
                    .orElse(Double.NaN);
            default:
                return null;
        }
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return Double.parseDouble(obj.toString());
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

    private static class AggregateGroup {
        Map<String, Object> groupValues = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
    }
}
