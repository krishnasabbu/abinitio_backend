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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executor for the Throttle node type, which rate-limits item processing based on a configurable maximum records-per-second.
 */
@Component
public class ThrottleExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ThrottleExecutor.class);

    private static final ThreadLocal<ThrottleState> throttleState = new ThreadLocal<>();

    private static class ThrottleState {
        long lastProcessedTime = 0;
        long intervalMs = 0;
        AtomicLong recordCount = new AtomicLong(0);
    }

    @Override
    public String getNodeType() {
        return "Throttle";
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

        JsonNode config = context.getNodeDefinition().getConfig();
        long maxRecordsPerSecond = config.has("maxRecordsPerSecond") ?
                config.get("maxRecordsPerSecond").asLong() : 1000;

        ThrottleState state = new ThrottleState();
        state.intervalMs = maxRecordsPerSecond > 0 ? (1000L / maxRecordsPerSecond) : 0;
        throttleState.set(state);

        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> {
            ThrottleState state = throttleState.get();
            if (state != null && state.intervalMs > 0) {
                long now = System.currentTimeMillis();
                long elapsed = now - state.lastProcessedTime;

                if (elapsed < state.intervalMs) {
                    long sleepTime = state.intervalMs - elapsed;
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                state.lastProcessedTime = System.currentTimeMillis();
                state.recordCount.incrementAndGet();
            }

            return item;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        String nodeId = context.getNodeDefinition().getId();
        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputItems.add(item);
                }
            }

            try {
                ThrottleState state = throttleState.get();
                if (state != null) {
                    long count = state.recordCount.get();
                    logger.debug("nodeId={}, Throttle processed {} records", nodeId, count);
                }
            } finally {
                throttleState.remove();
            }

            logger.info("nodeId={}, Throttle writing {} items to routing context", nodeId, outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("maxRecordsPerSecond")) {
            throw new IllegalArgumentException("Throttle node requires 'maxRecordsPerSecond' in config");
        }

        long maxRecords = config.get("maxRecordsPerSecond").asLong();
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("Throttle 'maxRecordsPerSecond' must be > 0");
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
