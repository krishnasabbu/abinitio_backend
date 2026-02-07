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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor that produces audit records for data items, logging selected fields to a configurable target.
 */
@Component
public class AuditExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(AuditExecutor.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    @Override
    public String getNodeType() {
        return "Audit";
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
        String auditFieldsStr = config.has("auditFields") ? config.get("auditFields").asText() : "";
        String target = config.has("target") ? config.get("target").asText() : "LOG";
        String nodeId = context.getNodeDefinition().getId();

        List<String> auditFields = parseArray(auditFieldsStr);

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;
                outputItems.add(item);

                try {
                    if (!target.equalsIgnoreCase("LOG")) {
                        throw new UnsupportedOperationException("Audit target '" + target + "' is not supported. Only 'LOG' is implemented.");
                    }

                    Map<String, Object> auditRecord = new LinkedHashMap<>();
                    auditRecord.put("timestamp", System.currentTimeMillis());
                    auditRecord.put("nodeId", nodeId);

                    if (auditFields.isEmpty()) {
                        auditRecord.put("data", item);
                    } else {
                        Map<String, Object> selectedData = new LinkedHashMap<>();
                        for (String field : auditFields) {
                            if (item.containsKey(field)) {
                                selectedData.put(field, item.get(field));
                            }
                        }
                        auditRecord.put("data", selectedData);
                    }

                    auditLogger.info("{}", formatAuditRecord(auditRecord));
                } catch (UnsupportedOperationException e) {
                    throw e;
                } catch (Exception e) {
                    logger.error("nodeId={}, Failed to emit audit record", nodeId, e);
                    throw new RuntimeException("Audit failed: " + e.getMessage(), e);
                }
            }

            logger.info("nodeId={}, Audit writing {} items to routing context", nodeId, outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    private String formatAuditRecord(Map<String, Object> auditRecord) {
        StringBuilder sb = new StringBuilder();
        sb.append("AuditRecord{");
        for (Map.Entry<String, Object> entry : auditRecord.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    private List<String> parseArray(String arrayStr) {
        List<String> result = new ArrayList<>();
        if (arrayStr == null || arrayStr.trim().isEmpty()) {
            return result;
        }
        for (String item : arrayStr.split(",")) {
            result.add(item.trim());
        }
        return result;
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("Audit node requires config");
        }

        if (config.has("target")) {
            String target = config.get("target").asText();
            if (!target.matches("LOG|DB")) {
                throw new IllegalArgumentException("Audit 'target' must be one of: LOG, DB");
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
