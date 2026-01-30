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
public class HashPartitionExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "HashPartition";
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
        String hashKeysStr = config.has("hashKeys") ? config.get("hashKeys").asText() : "";
        int partitions = config.has("partitions") ? config.get("partitions").asInt() : 3;

        List<String> hashKeys = parseArray(hashKeysStr);

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                Map<String, Object> enriched = new LinkedHashMap<>(item);
                int partition = computeHashPartition(enriched, hashKeys, partitions);
                enriched.put("_partition", partition);
                outputItems.add(enriched);
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    private int computeHashPartition(Map<String, Object> item, List<String> keys, int partitions) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            Object val = item.get(key);
            sb.append(val != null ? val.toString() : "null");
            sb.append("|");
        }

        int hash = sb.toString().hashCode();
        return Math.abs(hash % partitions);
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
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("hashKeys")) {
            throw new IllegalArgumentException("HashPartition node requires 'hashKeys' in config");
        }

        if (config.has("partitions")) {
            int partitions = config.get("partitions").asInt();
            if (partitions <= 0) {
                throw new IllegalArgumentException("HashPartition 'partitions' must be > 0");
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
}
