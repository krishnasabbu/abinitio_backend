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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DecryptExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(DecryptExecutor.class);

    @Override
    public String getNodeType() {
        return "Decrypt";
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
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String fieldsStr = config.has("fields") ? config.get("fields").asText() : "";
        String algorithm = config.has("algorithm") ? config.get("algorithm").asText() : "base64";

        List<String> fields = new ArrayList<>();
        if (!fieldsStr.trim().isEmpty()) {
            for (String f : fieldsStr.split(",")) { fields.add(f.trim()); }
        }

        return item -> {
            if (fields.isEmpty()) {
                return item;
            }
            Map<String, Object> result = new LinkedHashMap<>(item);
            for (String field : fields) {
                Object val = result.get(field);
                if (val instanceof String) {
                    result.put(field, decryptValue((String) val, algorithm));
                }
            }
            return result;
        };
    }

    private String decryptValue(String value, String algorithm) {
        if ("base64".equalsIgnoreCase(algorithm)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(value);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                logger.warn("Failed to base64 decode value: {}", e.getMessage());
                return value;
            }
        }
        return value;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, Decrypt output: {} items processed", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=Decrypt, nodeId=" + context.getNodeDefinition().getId()
                + ", missing config object");
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
