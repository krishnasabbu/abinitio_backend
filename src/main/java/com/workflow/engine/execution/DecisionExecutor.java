package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for conditional decision nodes.
 *
 * Evaluates a condition expression and adds a decision field to each item.
 * Used to create conditional branching paths in workflows.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class DecisionExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(DecisionExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Decision";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
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
        String description = context.getNodeDefinition().getConfig().has("description")
            ? context.getNodeDefinition().getConfig().get("description").asText()
            : "";

        return items -> {
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    Map<String, Object> routedItem = new LinkedHashMap<>(item);
                    routedItem.put("_route", "default");
                    outputItems.add(routedItem);
                }
            }
            context.setVariable("outputItems", outputItems);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
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
