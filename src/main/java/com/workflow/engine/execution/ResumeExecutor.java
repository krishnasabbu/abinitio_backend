package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor for resume nodes that continue workflow execution from a previously saved checkpoint.
 */
@Component
public class ResumeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ResumeExecutor.class);

    @Override
    public String getNodeType() {
        return "Resume";
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
        String checkpointId = config.has("checkpointId") ? config.get("checkpointId").asText() : "";
        String nodeId = context.getNodeDefinition().getId();

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputItems.add(item);
                }
            }

            try {
                String checkpointKey = "checkpoint_" + checkpointId;
                Object checkpointData = context.getVariable(checkpointKey);

                if (checkpointData == null) {
                    throw new IllegalStateException("Checkpoint not found: " + checkpointId);
                }

                logger.debug("nodeId={}, Resume continuing from checkpoint {}", nodeId, checkpointId);
            } catch (Exception e) {
                logger.error("nodeId={}, Failed to resume from checkpoint: {}", nodeId, checkpointId, e);
                throw new RuntimeException("Resume failed: " + e.getMessage(), e);
            }

            logger.info("nodeId={}, Resume writing {} items to routing context", nodeId, outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("checkpointId")) {
            throw new IllegalArgumentException("Resume node requires 'checkpointId' in config");
        }

        String checkpointId = config.get("checkpointId").asText();
        if (checkpointId == null || checkpointId.trim().isEmpty()) {
            throw new IllegalArgumentException("Resume 'checkpointId' cannot be empty");
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
