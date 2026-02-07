package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.EdgeBufferStore;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LookupExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(LookupExecutor.class);

    @Override
    public String getNodeType() {
        return "Lookup";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            EdgeBufferStore bufferStore = routingCtx.getRoutingContext().getBufferStore();

            logger.debug("Using BufferedItemReader for Lookup node {} port 'main'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "main", bufferStore);
        }

        List<Map<String, Object>> mainItems = (List<Map<String, Object>>) context.getVariable("mainInputItems");
        if (mainItems == null) {
            mainItems = new ArrayList<>();
        }
        return new ListItemReader<>(mainItems);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String joinKeysStr = config.has("joinKeys") ? config.get("joinKeys").asText() : "";
        boolean cacheEnabled = config.has("cacheSize") && config.get("cacheSize").asInt() > 0;

        List<String> joinKeys = parseArray(joinKeysStr);

        List<Map<String, Object>> tempLookupItems;
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            EdgeBufferStore bufferStore = routingCtx.getRoutingContext().getBufferStore();

            tempLookupItems = bufferStore.getRecords(executionId, nodeId, "lookup");
            logger.debug("Lookup node {} read {} lookup items from EdgeBufferStore port 'lookup'", nodeId, tempLookupItems.size());
        } else {
            tempLookupItems = (List<Map<String, Object>>) context.getVariable("lookupInputItems");
            if (tempLookupItems == null) {
                tempLookupItems = new ArrayList<>();
            }
        }
        final List<Map<String, Object>> lookupItems = tempLookupItems;

        Map<String, Map<String, Object>> lookupIndex = buildIndex(lookupItems, joinKeys);

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            List<Map<String, Object>> missItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                String keyStr = buildKeyString(item, joinKeys);
                Map<String, Object> lookupItem = lookupIndex.get(keyStr);

                Map<String, Object> enriched = new LinkedHashMap<>(item);
                if (lookupItem != null) {
                    for (Map.Entry<String, Object> entry : lookupItem.entrySet()) {
                        if (!enriched.containsKey(entry.getKey())) {
                            enriched.put(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    for (Map<String, Object> refItem : lookupItems) {
                        for (String field : refItem.keySet()) {
                            if (!enriched.containsKey(field) && !joinKeys.contains(field)) {
                                enriched.put(field, null);
                            }
                        }
                    }
                    missItems.add(enriched);
                }

                outputItems.add(enriched);
            }

            context.setVariable("outputItems", outputItems);
            context.setVariable("missItems", missItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("joinKeys")) {
            throw new IllegalArgumentException("Lookup node requires 'joinKeys' in config");
        }
    }

    private Map<String, Map<String, Object>> buildIndex(List<Map<String, Object>> items, List<String> keys) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            if (item != null) {
                String keyStr = buildKeyString(item, keys);
                if (!index.containsKey(keyStr)) {
                    index.put(keyStr, item);
                }
            }
        }
        return index;
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
