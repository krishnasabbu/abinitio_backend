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
 * Executor for partition nodes that distribute items across partitions based on a configurable strategy.
 */
@Component
public class PartitionExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(PartitionExecutor.class);

    @Override
    public String getNodeType() {
        return "Partition";
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
        int partitionCount = config.has("partitionCount") ? config.get("partitionCount").asInt() : 3;
        String strategy = config.has("strategy") ? config.get("strategy").asText() : "hash";

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            int index = 0;

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                Map<String, Object> enriched = new LinkedHashMap<>(item);
                int partition = assignPartition(enriched, index, strategy, partitionCount);
                enriched.put("_partition", partition);
                outputItems.add(enriched);
                index++;
            }

            logger.info("PartitionExecutor writing {} items", outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    private int assignPartition(Map<String, Object> item, int index, String strategy, int partitionCount) {
        if ("roundRobin".equalsIgnoreCase(strategy)) {
            return index % partitionCount;
        }
        return index % partitionCount;
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("Partition node requires config");
        }

        if (config.has("partitionCount")) {
            int count = config.get("partitionCount").asInt();
            if (count <= 0) {
                throw new IllegalArgumentException("Partition 'partitionCount' must be > 0");
            }
        }

        if (config.has("strategy")) {
            String strategy = config.get("strategy").asText();
            if (!strategy.matches("hash|roundRobin|range")) {
                throw new IllegalArgumentException("Partition 'strategy' must be one of: hash, roundRobin, range");
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
