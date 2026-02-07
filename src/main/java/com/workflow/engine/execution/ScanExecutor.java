package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScanExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ScanExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Scan";
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
        JsonNode config = context.getNodeDefinition().getConfig();
        String condition = config.has("condition") ? config.get("condition").asText() : null;
        String selectFieldsStr = config.has("selectFields") ? config.get("selectFields").asText() : "";

        List<String> selectFields = new ArrayList<>();
        if (!selectFieldsStr.trim().isEmpty()) {
            for (String f : selectFieldsStr.split(",")) { selectFields.add(f.trim()); }
        }

        return item -> {
            if (condition != null && !condition.trim().isEmpty()) {
                try {
                    SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                    Object result = parser.parseExpression(condition).getValue(evalContext, item);
                    if (result instanceof Boolean && !(Boolean) result) {
                        return null;
                    }
                } catch (Exception e) {
                    logger.warn("Scan condition evaluation failed: {}", e.getMessage());
                    return null;
                }
            }

            if (!selectFields.isEmpty()) {
                Map<String, Object> projected = new LinkedHashMap<>();
                for (String field : selectFields) {
                    if (item.containsKey(field)) {
                        projected.put(field, item.get(field));
                    }
                }
                return projected;
            }

            return item;
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
            logger.info("nodeId={}, Scan wrote {} items", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=Scan, nodeId=" + context.getNodeDefinition().getId()
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
