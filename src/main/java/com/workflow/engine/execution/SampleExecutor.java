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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Executor for the Sample node type, which selects a subset of input items using random, first-N, or percentage-based sampling.
 */
@Component
public class SampleExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SampleExecutor.class);

    private static final Random random = new Random();

    @Override
    public String getNodeType() {
        return "Sample";
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
        String sampleType = config.get("sampleType").asText();
        int value = config.get("value").asInt();

        return items -> {
            List<Map<String, Object>> allItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    allItems.add(item);
                }
            }

            List<Map<String, Object>> sampledItems = new ArrayList<>();

            if ("random".equalsIgnoreCase(sampleType)) {
                sampledItems = sampleRandom(allItems, value);
            } else if ("firstN".equalsIgnoreCase(sampleType)) {
                sampledItems = sampleFirstN(allItems, value);
            } else if ("percentage".equalsIgnoreCase(sampleType)) {
                sampledItems = samplePercentage(allItems, value);
            }

            logger.info("Sample writer produced {} items from {} input items", sampledItems.size(), allItems.size());
            context.setVariable("outputItems", sampledItems);
        };
    }

    private List<Map<String, Object>> sampleRandom(List<Map<String, Object>> items, int count) {
        if (items.size() <= count) {
            return new ArrayList<>(items);
        }

        List<Map<String, Object>> result = new ArrayList<>(items);
        Collections.shuffle(result, random);
        return new ArrayList<>(result.subList(0, count));
    }

    private List<Map<String, Object>> sampleFirstN(List<Map<String, Object>> items, int count) {
        if (items.size() <= count) {
            return new ArrayList<>(items);
        }
        return new ArrayList<>(items.subList(0, count));
    }

    private List<Map<String, Object>> samplePercentage(List<Map<String, Object>> items, int percentage) {
        if (percentage <= 0) {
            return new ArrayList<>();
        }
        if (percentage >= 100) {
            return new ArrayList<>(items);
        }

        int count = (int) Math.floor(items.size() * percentage / 100.0);
        return sampleFirstN(items, count);
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("sampleType")) {
            throw new IllegalArgumentException("Sample node requires 'sampleType' in config");
        }

        if (!config.has("value")) {
            throw new IllegalArgumentException("Sample node requires 'value' in config");
        }

        String sampleType = config.get("sampleType").asText();
        int value = config.get("value").asInt();

        if (!sampleType.matches("random|firstN|percentage")) {
            throw new IllegalArgumentException("Sample 'sampleType' must be one of: random, firstN, percentage");
        }

        if (value < 0) {
            throw new IllegalArgumentException("Sample 'value' must be >= 0");
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
