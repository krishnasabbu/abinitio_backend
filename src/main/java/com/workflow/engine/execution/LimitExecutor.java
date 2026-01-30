package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LimitExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Limit";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
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
        int limit = config.get("limit").asInt();
        int offset = config.get("offset").asInt();

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

            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("limit")) {
            throw new IllegalArgumentException("Limit node requires 'limit' in config");
        }

        if (!config.has("offset")) {
            throw new IllegalArgumentException("Limit node requires 'offset' in config");
        }

        int limit = config.get("limit").asInt();
        int offset = config.get("offset").asInt();

        if (limit < 0) {
            throw new IllegalArgumentException("Limit 'limit' must be >= 0");
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Limit 'offset' must be >= 0");
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
