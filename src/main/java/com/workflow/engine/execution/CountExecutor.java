package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CountExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Count";
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

            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("outputFieldName")) {
            throw new IllegalArgumentException("Count node requires 'outputFieldName' in config");
        }

        String outputFieldName = config.get("outputFieldName").asText();
        if (outputFieldName == null || outputFieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("Count 'outputFieldName' cannot be empty");
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
