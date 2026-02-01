package com.workflow.engine.execution.routing;

import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class RoutingItemWriter implements ItemWriter<Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(RoutingItemWriter.class);

    private final RoutingContext routingContext;
    private final String routeKeyField;

    public RoutingItemWriter(RoutingContext routingContext, String routeKeyField) {
        this.routingContext = routingContext;
        this.routeKeyField = routeKeyField != null ? routeKeyField : "_routePort";
    }

    @Override
    public void write(Chunk<? extends Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }

            Object routeKeyValue = item.get(routeKeyField);
            String routeKey = routeKeyValue != null ? routeKeyValue.toString() : null;

            try {
                if (routeKey != null && !routeKey.isEmpty()) {
                    routingContext.routeRecord(item, routeKey);
                } else {
                    routingContext.routeToDefault(item);
                }
            } catch (Exception e) {
                logger.error("Failed to route record from {} with key {}", routingContext.getSourceNodeId(), routeKey, e);
                throw new RuntimeException("Routing failed: " + e.getMessage(), e);
            }
        }
    }
}
