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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AssertExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Assert";
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
        JsonNode config = context.getNodeDefinition().getConfig();
        String assertionExpression = config.get("assertionExpression").asText();
        String errorMessage = config.get("errorMessage").asText();

        return item -> {
            try {
                StandardEvaluationContext evalContext = new StandardEvaluationContext(item);
                Object result = parser.parseExpression(assertionExpression).getValue(evalContext);

                boolean condition = false;
                if (result instanceof Boolean) {
                    condition = (Boolean) result;
                } else if (result != null) {
                    condition = Boolean.parseBoolean(result.toString());
                }

                if (!condition) {
                    throw new AssertionError(errorMessage);
                }

                return item;
            } catch (AssertionError ae) {
                throw ae;
            } catch (Exception e) {
                throw new AssertionError("Assert expression evaluation failed: " + e.getMessage(), e);
            }
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
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("assertionExpression")) {
            throw new IllegalArgumentException("Assert node requires 'assertionExpression' in config");
        }

        if (!config.has("errorMessage")) {
            throw new IllegalArgumentException("Assert node requires 'errorMessage' in config");
        }

        String assertionExpression = config.get("assertionExpression").asText();
        String errorMessage = config.get("errorMessage").asText();

        if (assertionExpression == null || assertionExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("Assert 'assertionExpression' cannot be empty");
        }

        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Assert 'errorMessage' cannot be empty");
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
