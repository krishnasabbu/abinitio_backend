package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for broadcast nodes that replicate each input item to multiple target nodes.
 */
@Component
public class BroadcastExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastExecutor.class);

    @Override
    public String getNodeType() {
        return "Broadcast";
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
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String targetNodesStr = config.has("targetNodes") ? config.get("targetNodes").asText() : "";
        List<String> targetNodes = parseArray(targetNodesStr);

        int numberOfCopies = Math.max(targetNodes.size(), 1);
        if (numberOfCopies == 1 && targetNodes.isEmpty()) {
            numberOfCopies = 3;
        }

        final int copies = numberOfCopies;

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                for (int i = 1; i <= copies; i++) {
                    Map<String, Object> broadcast = new LinkedHashMap<>(item);
                    broadcast.put("_broadcastTargets", targetNodes);
                    broadcast.put("_replicaIndex", i);
                    outputItems.add(broadcast);
                }
            }

            logger.info("BroadcastExecutor writing {} items", outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
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
        if (config == null) {
            throw new IllegalArgumentException("Broadcast node requires config");
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
