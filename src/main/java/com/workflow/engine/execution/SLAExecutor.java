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
 * Executor for SLA nodes that enforce service level agreement constraints on workflow execution time.
 */
@Component
public class SLAExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SLAExecutor.class);

    private static final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public String getNodeType() {
        return "SLA";
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
        startTime.set(System.currentTimeMillis());

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
        long maxDurationMs = config.has("maxDurationMs") ? config.get("maxDurationMs").asLong() : Long.MAX_VALUE;
        String action = config.has("action") ? config.get("action").asText() : "FAIL_JOB";
        String nodeId = context.getNodeDefinition().getId();

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputItems.add(item);
                }
            }

            try {
                Long start = startTime.get();
                if (start != null) {
                    long duration = System.currentTimeMillis() - start;

                    if (duration > maxDurationMs) {
                        String message = String.format("SLA exceeded: %dms > %dms", duration, maxDurationMs);

                        if ("WARN".equalsIgnoreCase(action)) {
                            logger.warn("nodeId={}, {}", nodeId, message);
                        } else if ("FAIL_JOB".equalsIgnoreCase(action)) {
                            logger.error("nodeId={}, {}", nodeId, message);
                            throw new RuntimeException("SLA violation: " + message);
                        }
                    } else {
                        logger.debug("nodeId={}, SLA check passed: {}ms <= {}ms", nodeId, duration, maxDurationMs);
                    }
                }
            } finally {
                startTime.remove();
            }

            logger.info("nodeId={}, SLA writing {} items to routing context", nodeId, outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("maxDurationMs")) {
            throw new IllegalArgumentException("SLA node requires 'maxDurationMs' in config");
        }

        long maxDuration = config.get("maxDurationMs").asLong();
        if (maxDuration <= 0) {
            throw new IllegalArgumentException("SLA 'maxDurationMs' must be > 0");
        }

        if (config.has("action")) {
            String action = config.get("action").asText();
            if (!action.matches("WARN|FAIL_JOB")) {
                throw new IllegalArgumentException("SLA 'action' must be one of: WARN, FAIL_JOB");
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
