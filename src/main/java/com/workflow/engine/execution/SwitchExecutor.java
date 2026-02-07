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
import com.workflow.engine.execution.routing.RoutingItemWriter;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SwitchExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SwitchExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Switch";
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
        String rulesStr = config.has("rules") ? config.get("rules").asText() : "";
        String nodeId = context.getNodeDefinition().getId();

        Map<String, String> rules = parseKeyValue(rulesStr);

        return item -> {
            String route = "default";
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                String condition = rule.getKey();
                String output = rule.getValue();

                try {
                    SimpleEvaluationContext evalCtx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                    Object result = parser.parseExpression(condition).getValue(evalCtx, item);
                    if (result instanceof Boolean && (Boolean) result) {
                        route = output;
                        break;
                    }
                } catch (Exception e) {
                    logger.warn("nodeId={}, Switch rule '{}' evaluation failed: {}", nodeId, condition, e.getMessage());
                }
            }

            Map<String, Object> routedItem = new LinkedHashMap<>(item);
            routedItem.put("_routePort", route);
            logger.debug("nodeId={}, Switch routed item to port '{}'", nodeId, route);
            return routedItem;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            RoutingItemWriter routingWriter = new RoutingItemWriter(routingCtx.getRoutingContext(), "_routePort");
            return items -> {
                logger.info("nodeId={}, Switch routing {} items", context.getNodeDefinition().getId(), items.size());
                routingWriter.write(items);
            };
        }
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, Switch writing {} items", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
    }

    private Map<String, String> parseKeyValue(String keyValueStr) {
        Map<String, String> result = new LinkedHashMap<>();
        if (keyValueStr == null || keyValueStr.trim().isEmpty()) {
            return result;
        }

        for (String line : keyValueStr.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String key = line.substring(0, colonIdx).trim();
                    String value = line.substring(colonIdx + 1).trim();
                    result.put(key, value);
                }
            }
        }

        return result;
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
