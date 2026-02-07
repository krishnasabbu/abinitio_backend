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
import java.util.List;
import java.util.Map;

/**
 * Executor that emits alerts based on configurable triggers and message templates during workflow execution.
 */
@Component
public class AlertExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(AlertExecutor.class);

    @Override
    public String getNodeType() {
        return "Alert";
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
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String alertType = config.has("alertType") ? config.get("alertType").asText() : "LOG";
        String messageTemplate = config.has("messageTemplate") ? config.get("messageTemplate").asText() : "";
        String trigger = config.has("trigger") ? config.get("trigger").asText() : "ALWAYS";
        String nodeId = context.getNodeDefinition().getId();

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputItems.add(item);
                }
            }

            try {
                if (!alertType.equalsIgnoreCase("LOG")) {
                    throw new UnsupportedOperationException("Alert type '" + alertType + "' is not supported. Only 'LOG' is implemented.");
                }

                if ("ALWAYS".equalsIgnoreCase(trigger) || "ON_SUCCESS".equalsIgnoreCase(trigger)) {
                    String message = interpolateTemplate(messageTemplate, context);
                    logger.info("nodeId={}, ALERT: {}", nodeId, message);
                } else if ("ON_FAILURE".equalsIgnoreCase(trigger)) {
                    logger.debug("nodeId={}, Alert trigger is ON_FAILURE but execution succeeded, skipping alert", nodeId);
                }
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Exception e) {
                logger.error("nodeId={}, Failed to emit alert", nodeId, e);
                throw new RuntimeException("Alert emission failed: " + e.getMessage(), e);
            }

            logger.info("nodeId={}, Alert writing {} items to routing context", nodeId, outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    private String interpolateTemplate(String template, NodeExecutionContext context) {
        if (template == null || template.isEmpty()) {
            return "No message";
        }

        String result = template;
        result = result.replace("{timestamp}", String.valueOf(System.currentTimeMillis()));
        result = result.replace("{nodeId}", context.getNodeDefinition().getId());

        return result;
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("Alert node requires config");
        }

        if (config.has("alertType")) {
            String alertType = config.get("alertType").asText();
            if (!alertType.matches("LOG|WEBHOOK|EMAIL")) {
                throw new IllegalArgumentException("Alert 'alertType' must be one of: LOG, WEBHOOK, EMAIL");
            }
        }

        if (config.has("trigger")) {
            String trigger = config.get("trigger").asText();
            if (!trigger.matches("ON_SUCCESS|ON_FAILURE|ALWAYS")) {
                throw new IllegalArgumentException("Alert 'trigger' must be one of: ON_SUCCESS, ON_FAILURE, ALWAYS");
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
