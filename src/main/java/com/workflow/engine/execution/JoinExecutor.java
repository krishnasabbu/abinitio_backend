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
public class JoinExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(JoinExecutor.class);

    @Override
    public String getNodeType() {
        return "Join";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            EdgeBufferStore bufferStore = routingCtx.getRoutingContext().getBufferStore();

            logger.debug("Using BufferedItemReader for Join node {} port 'left'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "left", bufferStore);
        }

        List<Map<String, Object>> leftItems = (List<Map<String, Object>>) context.getVariable("leftInputItems");
        if (leftItems == null) {
            leftItems = new ArrayList<>();
        }
        return new ListItemReader<>(leftItems);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String joinType = config.has("joinType") ? config.get("joinType").asText() : "inner";
        String leftKeysStr = config.has("leftKeys") ? config.get("leftKeys").asText() : "";
        String rightKeysStr = config.has("rightKeys") ? config.get("rightKeys").asText() : "";

        List<String> leftKeys = parseArray(leftKeysStr);
        List<String> rightKeys = parseArray(rightKeysStr);

        List<Map<String, Object>> rightItems;
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            EdgeBufferStore bufferStore = routingCtx.getRoutingContext().getBufferStore();

            rightItems = bufferStore.getRecords(executionId, nodeId, "right");
            logger.debug("Join node {} read {} right items from EdgeBufferStore port 'right'", nodeId, rightItems.size());
        } else {
            rightItems = (List<Map<String, Object>>) context.getVariable("rightInputItems");
            if (rightItems == null) {
                rightItems = new ArrayList<>();
            }
        }

        Map<String, Map<String, Object>> rightIndex = buildIndex(rightItems, rightKeys);

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> leftItem : items) {
                if (leftItem == null) continue;

                String leftKeyStr = buildKeyString(leftItem, leftKeys);
                Map<String, Object> rightItem = rightIndex.get(leftKeyStr);

                if (rightItem != null) {
                    Map<String, Object> joined = new LinkedHashMap<>(leftItem);
                    for (Map.Entry<String, Object> entry : rightItem.entrySet()) {
                        if (!joined.containsKey(entry.getKey())) {
                            joined.put(entry.getKey(), entry.getValue());
                        } else {
                            joined.put("right_" + entry.getKey(), entry.getValue());
                        }
                    }
                    outputItems.add(joined);
                } else {
                    if ("left".equals(joinType) || "full".equals(joinType)) {
                        Map<String, Object> nullJoined = new LinkedHashMap<>(leftItem);
                        for (String key : rightItem != null ? rightItem.keySet() : new ArrayList<String>()) {
                            nullJoined.put(key, null);
                        }
                        outputItems.add(nullJoined);
                    }
                }
            }

            if ("right".equals(joinType) || "full".equals(joinType)) {
                List<String> matchedRightKeys = new ArrayList<>();
                for (Map<String, Object> leftItem : items) {
                    if (leftItem != null) {
                        matchedRightKeys.add(buildKeyString(leftItem, leftKeys));
                    }
                }

                for (Map.Entry<String, Map<String, Object>> entry : rightIndex.entrySet()) {
                    if (!matchedRightKeys.contains(entry.getKey())) {
                        Map<String, Object> unmatched = new LinkedHashMap<>();
                        for (String leftKey : leftKeys) {
                            unmatched.put(leftKey, null);
                        }
                        unmatched.putAll(entry.getValue());
                        outputItems.add(unmatched);
                    }
                }
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("leftKeys") || !config.has("rightKeys")) {
            throw new IllegalArgumentException("Join node requires 'leftKeys' and 'rightKeys' in config");
        }
    }

    private Map<String, Map<String, Object>> buildIndex(List<Map<String, Object>> items, List<String> keys) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            if (item != null) {
                String keyStr = buildKeyString(item, keys);
                index.put(keyStr, item);
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
