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
public class CollectExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Collect";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items1 = (List<Map<String, Object>>) context.getVariable("in1InputItems");
        List<Map<String, Object>> items2 = (List<Map<String, Object>>) context.getVariable("in2InputItems");
        List<Map<String, Object>> items3 = (List<Map<String, Object>>) context.getVariable("in3InputItems");

        if (items1 == null) {
            items1 = new ArrayList<>();
        }
        if (items2 == null) {
            items2 = new ArrayList<>();
        }
        if (items3 == null) {
            items3 = new ArrayList<>();
        }

        List<Map<String, Object>> combined = new ArrayList<>(items1);
        combined.addAll(items2);
        combined.addAll(items3);

        return new ListItemReader<>(combined);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String collectMode = config.has("collectMode") ? config.get("collectMode").asText() : "concat";
        boolean stripMetadata = config.has("stripMetadata") && config.get("stripMetadata").asBoolean();

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;
                outputItems.add(item);
            }

            if ("ordered".equalsIgnoreCase(collectMode)) {
                outputItems.sort(new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                        Object idx1 = o1.get("_partitionIndex");
                        Object idx2 = o2.get("_partitionIndex");

                        if (idx1 != null && idx2 != null) {
                            Long v1 = toLong(idx1);
                            Long v2 = toLong(idx2);
                            if (v1 != null && v2 != null) {
                                int cmp = v1.compareTo(v2);
                                if (cmp != 0) return cmp;
                            }
                        }

                        Object seq1 = o1.get("_sequence");
                        Object seq2 = o2.get("_sequence");
                        if (seq1 != null && seq2 != null) {
                            Long v1 = toLong(seq1);
                            Long v2 = toLong(seq2);
                            if (v1 != null && v2 != null) {
                                return v1.compareTo(v2);
                            }
                        }

                        return 0;
                    }

                    private Long toLong(Object obj) {
                        try {
                            if (obj instanceof Long) {
                                return (Long) obj;
                            }
                            if (obj instanceof Number) {
                                return ((Number) obj).longValue();
                            }
                            return Long.parseLong(obj.toString());
                        } catch (Exception e) {
                            return null;
                        }
                    }
                });
            }

            if (stripMetadata) {
                List<Map<String, Object>> strippedItems = new ArrayList<>();
                for (Map<String, Object> item : outputItems) {
                    Map<String, Object> cleaned = new LinkedHashMap<>(item);
                    cleaned.keySet().removeIf(k -> k.startsWith("_"));
                    strippedItems.add(cleaned);
                }
                outputItems = strippedItems;
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("Collect node requires config");
        }

        if (config.has("collectMode")) {
            String mode = config.get("collectMode").asText();
            if (!mode.matches("concat|ordered")) {
                throw new IllegalArgumentException("Collect 'collectMode' must be one of: concat, ordered");
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
