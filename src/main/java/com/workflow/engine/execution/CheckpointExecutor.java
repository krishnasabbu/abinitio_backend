package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CheckpointExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointExecutor.class);

    @Override
    public String getNodeType() {
        return "Checkpoint";
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
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String checkpointId = config.has("checkpointId") ? config.get("checkpointId").asText() : "";
        String scope = config.has("scope") ? config.get("scope").asText() : "step";

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;
                outputItems.add(item);
            }

            try {
                long timestamp = System.currentTimeMillis();
                String checkpointKey = "checkpoint_" + checkpointId;

                Map<String, Object> checkpointData = new LinkedHashMap<>();
                checkpointData.put("checkpointId", checkpointId);
                checkpointData.put("timestamp", timestamp);
                checkpointData.put("scope", scope);
                checkpointData.put("recordCount", outputItems.size());

                if (!outputItems.isEmpty()) {
                    Map<String, Object> lastRecord = outputItems.get(outputItems.size() - 1);
                    if (lastRecord.containsKey("id")) {
                        checkpointData.put("lastProcessedId", lastRecord.get("id"));
                    }
                }

                context.setVariable(checkpointKey, checkpointData);
                logger.debug("Checkpoint persisted: {} with {} records", checkpointId, outputItems.size());
            } catch (Exception e) {
                logger.error("Failed to persist checkpoint: {}", checkpointId, e);
                throw new RuntimeException("Checkpoint persistence failed: " + e.getMessage(), e);
            }

            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("checkpointId")) {
            throw new IllegalArgumentException("Checkpoint node requires 'checkpointId' in config");
        }

        String checkpointId = config.get("checkpointId").asText();
        if (checkpointId == null || checkpointId.trim().isEmpty()) {
            throw new IllegalArgumentException("Checkpoint 'checkpointId' cannot be empty");
        }

        if (config.has("scope")) {
            String scope = config.get("scope").asText();
            if (!scope.matches("step|job")) {
                throw new IllegalArgumentException("Checkpoint 'scope' must be one of: step, job");
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
