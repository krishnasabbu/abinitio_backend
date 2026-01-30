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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for field-level mapping and renaming operations.
 *
 * Transforms input items by renaming, selecting, and reordering fields according
 * to a field mapping configuration. Creates new items with the mapped field names.
 *
 * Configuration:
 * - mappings: (required) Multi-line string of sourceField:targetField pairs
 *             Each line contains one mapping in format "sourceField: targetField"
 *             Example: "firstName: first_name\nlastName: last_name"
 *
 * Output:
 * Sets "outputItems" variable with the mapped items.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class MapExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(MapExecutor.class);

    @Override
    public String getNodeType() {
        return "Map";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating map reader", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            logger.debug("nodeId={}, No input items found, using empty list", context.getNodeDefinition().getId());
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating map processor", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();
        String mappingsStr = config.get("mappings").asText();

        Map<String, String> mappings = parseKeyValue(mappingsStr);
        logger.debug("nodeId={}, Parsed {} field mappings", context.getNodeDefinition().getId(), mappings.size());

        return item -> {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                String sourceField = mapping.getKey();
                String targetField = mapping.getValue();

                Object value = item.get(sourceField);
                result.put(targetField, value);
            }

            return result;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating map writer", context.getNodeDefinition().getId());
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, Map output: {} items processed", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating Map configuration", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("mappings")) {
            logger.error("nodeId={}, Configuration invalid - missing 'mappings' property", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Map node requires 'mappings' in config");
        }

        String mappingsStr = config.get("mappings").asText();
        if (mappingsStr == null || mappingsStr.trim().isEmpty()) {
            logger.error("nodeId={}, Configuration invalid - 'mappings' cannot be empty", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Map 'mappings' cannot be empty");
        }
        logger.debug("nodeId={}, Map configuration valid", context.getNodeDefinition().getId());
    }

    /**
     * Parses key-value pairs from newline-separated string.
     *
     * @param keyValueStr string containing key:value pairs separated by newlines
     * @return map of parsed key-value pairs
     */
    private Map<String, String> parseKeyValue(String keyValueStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (keyValueStr == null || keyValueStr.trim().isEmpty()) {
            return result;
        }

        for (String line : keyValueStr.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String key = line.substring(0, colonIdx).trim();
                    String value = line.substring(colonIdx + 1).trim();
                    result.put(key, value);
                }
            }
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
