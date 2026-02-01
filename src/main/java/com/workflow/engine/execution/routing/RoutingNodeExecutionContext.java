package com.workflow.engine.execution.routing;

import com.workflow.engine.execution.NodeExecutionContext;
import com.workflow.engine.model.NodeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class RoutingNodeExecutionContext extends NodeExecutionContext {

    private static final Logger logger = LoggerFactory.getLogger(RoutingNodeExecutionContext.class);

    private final RoutingContext routingContext;

    public RoutingNodeExecutionContext(NodeDefinition nodeDefinition, org.springframework.batch.core.StepExecution stepExecution, RoutingContext routingContext) {
        super(nodeDefinition, stepExecution);
        this.routingContext = routingContext;
    }

    @Override
    public void setVariable(String key, Object value) {
        if ("outputItems".equals(key) && value instanceof List) {
            handleOutputRouting((List<?>) value);
        } else {
            super.setVariable(key, value);
        }
    }

    private void handleOutputRouting(List<?> outputItems) {
        if (outputItems == null || outputItems.isEmpty()) {
            logger.debug("No output items to route from node {}", getNodeDefinition().getId());
            return;
        }

        logger.debug("Routing {} output items from node {}", outputItems.size(), getNodeDefinition().getId());

        for (Object item : outputItems) {
            if (item instanceof Map) {
                Map<String, Object> record = (Map<String, Object>) item;
                Object routePort = record.get("_routePort");
                String routeKey = routePort != null ? routePort.toString() : null;

                if (routeKey != null) {
                    logger.debug("Item has explicit route port: {}", routeKey);
                    routingContext.routeRecord(record, routeKey);
                } else {
                    logger.debug("Item routed to default port");
                    routingContext.routeToDefault(record);
                }
            }
        }
    }

    public RoutingContext getRoutingContext() {
        return routingContext;
    }
}
