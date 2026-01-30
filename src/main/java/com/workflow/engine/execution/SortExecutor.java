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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for sorting items based on field values.
 *
 * Sorts all input items according to one or more sort keys with configurable direction
 * (ascending or descending). Supports multi-level sorting by field priority.
 *
 * Configuration:
 * - sortKeys: (required) Multi-line string of field:direction pairs
 *             Each line contains one sort key in format "field: asc|desc"
 *             Example: "name: asc\nage: desc" sorts by name ascending, then age descending
 *
 * Output:
 * Sets "outputItems" variable with the sorted items.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class SortExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SortExecutor.class);

    @Override
    public String getNodeType() {
        return "Sort";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating sort reader", context.getNodeDefinition().getId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            logger.debug("nodeId={}, No input items", context.getNodeDefinition().getId());
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating sort processor (pass-through)", context.getNodeDefinition().getId());
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating sort writer", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();
        String sortKeysStr = config.has("sortKeys") ? config.get("sortKeys").asText() : "";

        Map<String, String> sortKeys = parseSortKeys(sortKeysStr);
        logger.debug("nodeId={}, Sort keys parsed: {} fields", context.getNodeDefinition().getId(), sortKeys.size());

        return items -> {
            List<Map<String, Object>> allItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    allItems.add(item);
                }
            }

            logger.debug("nodeId={}, Sorting {} items by {} fields", context.getNodeDefinition().getId(), allItems.size(), sortKeys.size());
            allItems.sort((a, b) -> {
                for (Map.Entry<String, String> sortSpec : sortKeys.entrySet()) {
                    String field = sortSpec.getKey();
                    String direction = sortSpec.getValue().toLowerCase();

                    Object aVal = a.get(field);
                    Object bVal = b.get(field);

                    int comparison = compareObjects(aVal, bVal);
                    if (comparison != 0) {
                        return direction.equals("desc") ? -comparison : comparison;
                    }
                }
                return 0;
            });

            logger.info("nodeId={}, Sort output: {} items sorted", context.getNodeDefinition().getId(), allItems.size());
            context.setVariable("outputItems", allItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("nodeId={}, Validating Sort configuration", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("sortKeys")) {
            logger.error("nodeId={}, Configuration invalid - missing 'sortKeys' property", context.getNodeDefinition().getId());
            throw new IllegalArgumentException("Sort node requires 'sortKeys' in config");
        }
        logger.debug("nodeId={}, Sort configuration valid", context.getNodeDefinition().getId());
    }

    /**
     * Compares two objects for sorting, handling nulls and comparable types.
     *
     * @param a first object to compare
     * @param b second object to compare
     * @return comparison result (-1, 0, or 1)
     */
    private int compareObjects(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b);
            } catch (Exception e) {
                return a.toString().compareTo(b.toString());
            }
        }

        return a.toString().compareTo(b.toString());
    }

    private Map<String, String> parseSortKeys(String sortKeysStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (sortKeysStr == null || sortKeysStr.trim().isEmpty()) {
            return result;
        }

        for (String line : sortKeysStr.split("\n")) {
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
