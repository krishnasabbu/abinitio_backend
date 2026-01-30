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
import java.util.TreeMap;

@Component
public class RangePartitionExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static class RangeBucket {
        String name;
        Long minVal;
        Long maxVal;
        boolean hasMax;
    }

    @Override
    public String getNodeType() {
        return "RangePartition";
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
        String rangeField = config.has("rangeField") ? config.get("rangeField").asText() : "";
        String rangesStr = config.has("ranges") ? config.get("ranges").asText() : "";

        List<RangeBucket> buckets = parseRanges(rangesStr);

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            int partitionIndex = 0;

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                Map<String, Object> enriched = new LinkedHashMap<>(item);
                Object fieldValue = item.get(rangeField);

                String bucket = findBucket(fieldValue, buckets);
                enriched.put("_rangeBucket", bucket);
                enriched.put("_partitionIndex", partitionIndex);
                outputItems.add(enriched);
                partitionIndex++;
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    private List<RangeBucket> parseRanges(String rangesStr) {
        List<RangeBucket> buckets = new ArrayList<>();
        if (rangesStr == null || rangesStr.trim().isEmpty()) {
            return buckets;
        }

        String[] rangePairs = rangesStr.split(",");
        for (String pair : rangePairs) {
            String[] parts = pair.trim().split(":");
            if (parts.length < 2) continue;

            String bucketName = parts[0].trim();
            String rangeSpec = parts[1].trim();

            RangeBucket bucket = new RangeBucket();
            bucket.name = bucketName;

            if (rangeSpec.endsWith("+")) {
                String minStr = rangeSpec.substring(0, rangeSpec.length() - 1).trim();
                bucket.minVal = Long.parseLong(minStr);
                bucket.maxVal = Long.MAX_VALUE;
                bucket.hasMax = false;
            } else if (rangeSpec.contains("-")) {
                String[] range = rangeSpec.split("-");
                bucket.minVal = Long.parseLong(range[0].trim());
                bucket.maxVal = Long.parseLong(range[1].trim());
                bucket.hasMax = true;
            }

            buckets.add(bucket);
        }

        return buckets;
    }

    private String findBucket(Object fieldValue, List<RangeBucket> buckets) {
        if (fieldValue == null) {
            return "unknown";
        }

        try {
            long val = Long.parseLong(fieldValue.toString());
            for (RangeBucket bucket : buckets) {
                if (val >= bucket.minVal && (bucket.hasMax ? val <= bucket.maxVal : true)) {
                    return bucket.name;
                }
            }
        } catch (NumberFormatException e) {
            return "unknown";
        }

        return "unknown";
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("rangeField")) {
            throw new IllegalArgumentException("RangePartition node requires 'rangeField' in config");
        }

        if (!config.has("ranges")) {
            throw new IllegalArgumentException("RangePartition node requires 'ranges' in config");
        }

        String rangesStr = config.get("ranges").asText();
        if (rangesStr == null || rangesStr.trim().isEmpty()) {
            throw new IllegalArgumentException("RangePartition 'ranges' cannot be empty");
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
