package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
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

/**
 * Executor for the Minus node type.
 * Computes the difference of items from two upstream inputs based on key fields.
 * Supports routing-aware execution via BufferedItemReader when running in routing mode.
 */
@Component
public class MinusExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(MinusExecutor.class);

    @Override
    public String getNodeType() {
        return "Minus";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            logger.debug("nodeId={}, Using BufferedItemReader for port 'in'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "in", routingCtx.getRoutingContext().getBufferStore());
        }
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

        List<Map<String, Object>> items2;
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            items2 = routingCtx.getRoutingContext().getBufferStore()
                .getRecords(executionId, nodeId, "in2");
            logger.debug("nodeId={}, Minus read {} items from 'in2' port", nodeId, items2.size());
        } else {
            items2 = (List<Map<String, Object>>) context.getVariable("in2InputItems");
            if (items2 == null) {
                items2 = new ArrayList<>();
            }
        }

        Map<String, Boolean> set2Index = new LinkedHashMap<>();
        for (Map<String, Object> item : items2) {
            if (item != null) {
                String keyStr = buildKeyString(item, keyFields);
                set2Index.put(keyStr, true);
            }
        }

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            Map<String, Boolean> emittedKeys = new LinkedHashMap<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                String keyStr = buildKeyString(item, keyFields);

                if (!set2Index.containsKey(keyStr) && !emittedKeys.containsKey(keyStr)) {
                    outputItems.add(item);
                    emittedKeys.put(keyStr, true);
                }
            }

            logger.info("nodeId={}, Writing {} items", context.getNodeDefinition().getId(), outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("keyFields")) {
            throw new IllegalArgumentException("Minus node requires 'keyFields' in config");
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
