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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for the Reject node type, which marks items as rejected with reason metadata and timestamps.
 */
@Component
public class RejectExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(RejectExecutor.class);

    @Override
    public String getNodeType() {
        return "Reject";
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
        JsonNode config = context.getNodeDefinition().getConfig();
        String rejectReasonField = config != null && config.has("rejectReasonField")
            ? config.get("rejectReasonField").asText()
            : "reject_reason";

        if (rejectReasonField == null || rejectReasonField.trim().isEmpty()) {
            rejectReasonField = "reject_reason";
        }

        final String finalRejectReasonField = rejectReasonField;
        String nodeId = context.getNodeDefinition().getId();

        return item -> {
            try {
                Map<String, Object> result = new LinkedHashMap<>(item);

                String rejectReason = determineRejectReason(item);
                result.put(finalRejectReasonField, rejectReason);

                if (!result.containsKey("_rejectedAt")) {
                    result.put("_rejectedAt", Instant.now().toString());
                }

                if (!result.containsKey("_rejectedBy")) {
                    result.put("_rejectedBy", nodeId != null ? nodeId : "Reject");
                }

                if (!result.containsKey("_rejectType")) {
                    boolean hasValidationMetadata = item.containsKey("_validationErrors")
                        || item.containsKey("_failedRules");
                    result.put("_rejectType", hasValidationMetadata ? "Validate" : "FilterOrManual");
                }

                return result;
            } catch (Exception e) {
                Map<String, Object> result = new LinkedHashMap<>(item);
                result.put(finalRejectReasonField, "Rejected");
                result.put("_rejectedAt", Instant.now().toString());
                result.put("_rejectedBy", nodeId != null ? nodeId : "Reject");
                result.put("_rejectType", "FilterOrManual");
                return result;
            }
        };
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
            logger.info("Reject writer produced {} items", outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config != null && config.has("rejectReasonField")) {
            String field = config.get("rejectReasonField").asText();
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("Reject 'rejectReasonField' cannot be empty if specified");
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

    private String determineRejectReason(Map<String, Object> item) {
        if (item.containsKey("_validationErrors")) {
            Object errors = item.get("_validationErrors");
            return joinToString(errors);
        }

        if (item.containsKey("_failedRules")) {
            Object rules = item.get("_failedRules");
            return joinToString(rules);
        }

        if (item.containsKey("_rejectReason")) {
            Object reason = item.get("_rejectReason");
            if (reason != null) {
                return reason.toString();
            }
        }

        return "Rejected";
    }

    private String joinToString(Object value) {
        if (value == null) {
            return "Rejected";
        }

        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "Rejected";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(list.get(i).toString());
            }
            return sb.toString();
        }

        return value.toString();
    }
}
