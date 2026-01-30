package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor for limiting and offsetting results.
 *
 * Implements pagination-style limiting by selecting a subset of items from offset
 * to offset+limit. Useful for implementing TOP-N queries or pagination.
 *
 * Configuration:
 * - limit: (required) Maximum number of items to output
 * - offset: (required) Starting position (0-based index) in the item list
 *
 * Output:
 * Sets "outputItems" variable with the limited/paginated items.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class LimitExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(LimitExecutor.class);

    @Override
    public String getNodeType() {
        return "Limit";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating limit reader", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            logger.debug("nodeId={}, No input items", context.getNodeDefinition().getId());
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating limit processor (pass-through)", context.getNodeDefinition().getId());
        return new ItemProcessor<Map<String, Object>, Map<String, Object>>() {
            @Override
            public Map<String, Object> process(Map<String, Object> item) throws Exception {
                return item;
            }
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating limit writer", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();
        int limit = config.get("limit").asInt();
        int offset = config.get("offset").asInt();
        logger.debug("nodeId={}, Limit configuration: offset={}, limit={}", context.getNodeDefinition().getId(), offset, limit);

        return items -> {
            List<Map<String, Object>> allItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    allItems.add(item);
                }
            }

            List<Map<String, Object>> outputList = new ArrayList<>();

            int startIndex = Math.max(0, offset);
            int endIndex = Math.min(allItems.size(), startIndex + limit);

            if (startIndex < allItems.size()) {
                outputList.addAll(allItems.subList(startIndex, endIndex));
            }

            logger.info("nodeId={}, Limit output: {} items selected from {} (offset={}, limit={})",
                context.getNodeDefinition().getId(), outputList.size(), allItems.size(), offset, limit);
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating Limit configuration", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("limit")) {
            logger.error("nodeId={}, Configuration invalid - missing 'limit' property", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Limit node requires 'limit' in config");
        }

        if (!config.has("offset")) {
            logger.error("nodeId={}, Configuration invalid - missing 'offset' property", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Limit node requires 'offset' in config");
        }

        int limit = config.get("limit").asInt();
        int offset = config.get("offset").asInt();

        if (limit < 0) {
            logger.error("nodeId={}, Configuration invalid - 'limit' must be >= 0 (got {})", context.getNodeDefinition().getId(), limit);
            throw new IllegalArgumentException("Limit 'limit' must be >= 0");
        }

        if (offset < 0) {
            logger.error("nodeId={}, Configuration invalid - 'offset' must be >= 0 (got {})", context.getNodeDefinition().getId(), offset);
            throw new IllegalArgumentException("Limit 'offset' must be >= 0");
        }
        logger.debug("nodeId={}, Limit configuration valid", context.getNodeDefinition().getId());
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
