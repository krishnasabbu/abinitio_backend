package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SplitExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SplitExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Split";
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
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String splitField = config.has("splitField") ? config.get("splitField").asText() : null;
        String condition = config.has("condition") ? config.get("condition").asText() : null;

        JsonNode routesNode = config.has("routes") ? config.get("routes") : null;
        Map<String, String> routes = new LinkedHashMap<>();
        if (routesNode != null && routesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = routesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                routes.put(entry.getKey(), entry.getValue().asText());
            }
        }

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;
                Map<String, Object> routedItem = new LinkedHashMap<>(item);

                if (!routes.isEmpty()) {
                    for (Map.Entry<String, String> route : routes.entrySet()) {
                        String portName = route.getKey();
                        String expr = route.getValue();
                        try {
                            SimpleEvaluationContext evalCtx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                            Object result = parser.parseExpression(expr).getValue(evalCtx, item);
                            if (result instanceof Boolean && (Boolean) result) {
                                Map<String, Object> copy = new LinkedHashMap<>(item);
                                copy.put("_routePort", portName);
                                outputItems.add(copy);
                            }
                        } catch (Exception e) {
                            logger.warn("Split route '{}' condition failed: {}", portName, e.getMessage());
                        }
                    }
                } else if (splitField != null && !splitField.isEmpty()) {
                    Object val = item.get(splitField);
                    routedItem.put("_routePort", val != null ? val.toString() : "default");
                    outputItems.add(routedItem);
                } else {
                    outputItems.add(routedItem);
                }
            }

            logger.info("nodeId={}, Split wrote {} items", context.getNodeDefinition().getId(), outputItems.size());
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=Split, nodeId=" + context.getNodeDefinition().getId()
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
