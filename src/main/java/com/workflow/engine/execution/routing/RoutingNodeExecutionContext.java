package com.workflow.engine.execution.routing;

import com.workflow.engine.execution.NodeExecutionContext;
import com.workflow.engine.model.NodeDefinition;

import java.util.List;
import java.util.Map;

public class RoutingNodeExecutionContext extends NodeExecutionContext {

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
            return;
        }

        for (Object item : outputItems) {
            if (item instanceof Map) {
                Map<String, Object> record = (Map<String, Object>) item;
                Object routePort = record.get("_routePort");
                String routeKey = routePort != null ? routePort.toString() : null;

                if (routeKey != null) {
                    routingContext.routeRecord(record, routeKey);
                } else {
                    routingContext.routeToDefault(record);
                }
            }
        }
    }

    public RoutingContext getRoutingContext() {
        return routingContext;
    }
}
