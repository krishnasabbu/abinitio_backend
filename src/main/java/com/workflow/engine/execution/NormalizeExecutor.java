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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executor that normalizes data by expanding array or collection fields into individual rows.
 */
@Component
public class NormalizeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeExecutor.class);

    @Override
    public String getNodeType() {
        return "Normalize";
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
        String arrayField = config.get("arrayField").asText();
        String outputFieldsStr = config.has("outputFields") ? config.get("outputFields").asText() : null;

        Set<String> outputFields = new HashSet<>();
        if (outputFieldsStr != null && !outputFieldsStr.trim().isEmpty()) {
            for (String field : outputFieldsStr.split(",")) {
                outputFields.add(field.trim());
            }
        }

        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;

                Object arrayValue = item.get(arrayField);

                if (arrayValue == null) {
                    outputList.add(new LinkedHashMap<>(item));
                } else if (arrayValue instanceof Collection) {
                    Collection<?> collection = (Collection<?>) arrayValue;
                    for (Object element : collection) {
                        Map<String, Object> outputRow = new LinkedHashMap<>();

                        for (Map.Entry<String, Object> entry : item.entrySet()) {
                            if (!entry.getKey().equals(arrayField)) {
                                outputRow.put(entry.getKey(), entry.getValue());
                            }
                        }

                        if (element instanceof Map) {
                            Map<?, ?> elementMap = (Map<?, ?>) element;
                            if (outputFields.isEmpty()) {
                                for (Map.Entry<?, ?> entry : elementMap.entrySet()) {
                                    outputRow.put(entry.getKey().toString(), entry.getValue());
                                }
                            } else {
                                for (String field : outputFields) {
                                    outputRow.put(field, elementMap.get(field));
                                }
                            }
                        } else {
                            outputRow.put(arrayField, element);
                        }

                        outputList.add(outputRow);
                    }
                } else if (arrayValue instanceof Object[]) {
                    Object[] array = (Object[]) arrayValue;
                    for (Object element : array) {
                        Map<String, Object> outputRow = new LinkedHashMap<>();

                        for (Map.Entry<String, Object> entry : item.entrySet()) {
                            if (!entry.getKey().equals(arrayField)) {
                                outputRow.put(entry.getKey(), entry.getValue());
                            }
                        }

                        if (element instanceof Map) {
                            Map<?, ?> elementMap = (Map<?, ?>) element;
                            if (outputFields.isEmpty()) {
                                for (Map.Entry<?, ?> entry : elementMap.entrySet()) {
                                    outputRow.put(entry.getKey().toString(), entry.getValue());
                                }
                            } else {
                                for (String field : outputFields) {
                                    outputRow.put(field, elementMap.get(field));
                                }
                            }
                        } else {
                            outputRow.put(arrayField, element);
                        }

                        outputList.add(outputRow);
                    }
                } else {
                    outputList.add(new LinkedHashMap<>(item));
                }
            }

            logger.info("Normalize writer produced {} items", outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("arrayField")) {
            throw new IllegalArgumentException("Normalize node requires 'arrayField' in config");
        }

        String arrayField = config.get("arrayField").asText();
        if (arrayField == null || arrayField.trim().isEmpty()) {
            throw new IllegalArgumentException("Normalize 'arrayField' cannot be empty");
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
