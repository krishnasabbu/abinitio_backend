package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Executor that denormalizes data by grouping rows on key fields and collecting related values into nested lists.
 */
@Component
public class DenormalizeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(DenormalizeExecutor.class);

    @Override
    public String getNodeType() {
        return "Denormalize";
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
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return new ItemProcessor<Map<String, Object>, Map<String, Object>>() {
            @Override
            public Map<String, Object> process(Map<String, Object> item) throws Exception {
                return item;
            }
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String groupByKeysStr = config.get("groupByKeys").asText();
        String collectField = config.get("collectField").asText();

        List<String> groupByKeys = new ArrayList<>();
        for (String key : groupByKeysStr.split(",")) {
            groupByKeys.add(key.trim());
        }

        return items -> {
            Map<String, Map<String, Object>> groupedData = new TreeMap<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                StringBuilder keyBuilder = new StringBuilder();
                for (int i = 0; i < groupByKeys.size(); i++) {
                    if (i > 0) keyBuilder.append("|");
                    Object keyValue = item.get(groupByKeys.get(i));
                    keyBuilder.append(keyValue != null ? keyValue.toString() : "null");
                }

                String groupKey = keyBuilder.toString();

                if (!groupedData.containsKey(groupKey)) {
                    Map<String, Object> groupHeader = new LinkedHashMap<>();
                    for (String key : groupByKeys) {
                        groupHeader.put(key, item.get(key));
                    }
                    groupHeader.put(collectField, new ArrayList<>());
                    groupedData.put(groupKey, groupHeader);
                }

                Map<String, Object> groupRow = groupedData.get(groupKey);
                Map<String, Object> collectItem = new LinkedHashMap<>();

                for (Map.Entry<String, Object> entry : item.entrySet()) {
                    if (!groupByKeys.contains(entry.getKey())) {
                        collectItem.put(entry.getKey(), entry.getValue());
                    }
                }

                List<Map<String, Object>> collectedList = (List<Map<String, Object>>) groupRow.get(collectField);
                collectedList.add(collectItem);
            }

            List<Map<String, Object>> outputList = new ArrayList<>(groupedData.values());
            logger.info("Denormalize writer produced {} items", outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("groupByKeys")) {
            throw new IllegalArgumentException("Denormalize node requires 'groupByKeys' in config");
        }

        if (!config.has("collectField")) {
            throw new IllegalArgumentException("Denormalize node requires 'collectField' in config");
        }

        String groupByKeys = config.get("groupByKeys").asText();
        String collectField = config.get("collectField").asText();

        if (groupByKeys == null || groupByKeys.trim().isEmpty()) {
            throw new IllegalArgumentException("Denormalize 'groupByKeys' cannot be empty");
        }

        if (collectField == null || collectField.trim().isEmpty()) {
            throw new IllegalArgumentException("Denormalize 'collectField' cannot be empty");
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
