package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for counting input items.
 *
 * Counts all input items and produces a single output record containing the count.
 * Useful for generating statistics and metrics about data volumes.
 *
 * Configuration:
 * - outputFieldName: (required) Name of the field in output record to store the count
 *
 * Output:
 * Sets "outputItems" variable with a single-record list containing the count.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class CountExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(CountExecutor.class);

    @Override
    public String getNodeType() {
        return "Count";
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
        logger.debug("nodeId={}, Creating count reader", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            logger.debug("nodeId={}, No input items found", context.getNodeDefinition().getId());
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating count processor (pass-through)", context.getNodeDefinition().getId());
        return new ItemProcessor<Map<String, Object>, Map<String, Object>>() {
            @Override
            public Map<String, Object> process(Map<String, Object> item) throws Exception {
                return item;
            }
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating count writer", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();
        String outputFieldName = config.get("outputFieldName").asText();

        return items -> {
            int count = 0;
            for (Map<String, Object> item : items) {
                if (item != null) {
                    count++;
                }
            }

            Map<String, Object> outputRecord = new LinkedHashMap<>();
            outputRecord.put(outputFieldName, count);

            List<Map<String, Object>> outputList = new ArrayList<>();
            outputList.add(outputRecord);

            logger.info("nodeId={}, Count output: {} items counted, storing in field '{}'",
                context.getNodeDefinition().getId(), count, outputFieldName);
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating Count configuration", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("outputFieldName")) {
            logger.error("nodeId={}, Configuration invalid - missing 'outputFieldName' property", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Count node requires 'outputFieldName' in config");
        }

        String outputFieldName = config.get("outputFieldName").asText();
        if (outputFieldName == null || outputFieldName.trim().isEmpty()) {
            logger.error("nodeId={}, Configuration invalid - 'outputFieldName' cannot be empty", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Count 'outputFieldName' cannot be empty");
        }
        logger.debug("nodeId={}, Count configuration valid", context.getNodeDefinition().getId());
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
