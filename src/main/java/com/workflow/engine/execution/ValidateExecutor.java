package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingItemWriter;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for the Validate node type, which evaluates items against SpEL-based validation rules and separates valid from invalid items.
 */
@Component
public class ValidateExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ValidateExecutor.class);

    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Validate";
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
        String rulesStr = config.get("rules").asText();

        List<ValidationRule> rules = parseRules(rulesStr);
        boolean isRouting = context instanceof RoutingNodeExecutionContext;

        return item -> {
            List<String> validationErrors = new ArrayList<>();
            List<String> failedRules = new ArrayList<>();

            for (ValidationRule rule : rules) {
                try {
                    StandardEvaluationContext evalContext = new StandardEvaluationContext(item);
                    Object result = parser.parseExpression(rule.expression).getValue(evalContext);

                    boolean isValid = (result instanceof Boolean) && (Boolean) result;

                    if (!isValid) {
                        validationErrors.add(rule.message);
                        failedRules.add(rule.field);
                    }
                } catch (Exception e) {
                    validationErrors.add(rule.message);
                    failedRules.add(rule.field);
                }
            }

            if (validationErrors.isEmpty()) {
                if (isRouting) {
                    Map<String, Object> routed = new HashMap<>(item);
                    routed.put("_routePort", "out");
                    return routed;
                }
                return item;
            } else {
                Map<String, Object> result = new LinkedHashMap<>(item);
                result.put("_validationErrors", validationErrors);
                result.put("_failedRules", failedRules);
                if (isRouting) {
                    result.put("_routePort", "invalid");
                }
                return result;
            }
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        String nodeId = context.getNodeDefinition().getId();

        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            RoutingItemWriter routingWriter = new RoutingItemWriter(routingCtx.getRoutingContext(), "_routePort");
            return items -> {
                logger.info("nodeId={}, Validate routing {} items", nodeId, items.size());
                routingWriter.write(items);
            };
        }

        return items -> {
            List<Map<String, Object>> validOutputList = new ArrayList<>();
            List<Map<String, Object>> invalidOutputList = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item != null) {
                    if (item.containsKey("_validationErrors")) {
                        invalidOutputList.add(item);
                    } else {
                        validOutputList.add(item);
                    }
                }
            }

            logger.info("nodeId={}, Validate produced {} valid and {} invalid items", nodeId, validOutputList.size(), invalidOutputList.size());
            context.setVariable("outputItems", validOutputList);
            context.setVariable("invalidItems", invalidOutputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("rules")) {
            throw new IllegalArgumentException("Validate node requires 'rules' in config");
        }

        String rules = config.get("rules").asText();
        if (rules == null || rules.trim().isEmpty()) {
            throw new IllegalArgumentException("Validate 'rules' cannot be empty");
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

    private List<ValidationRule> parseRules(String rulesStr) {
        List<ValidationRule> rules = new ArrayList<>();

        if (rulesStr == null || rulesStr.trim().isEmpty()) {
            return rules;
        }

        for (String line : rulesStr.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                rules.add(parseRule(line));
            }
        }

        return rules;
    }

    private ValidationRule parseRule(String line) {
        String[] parts = line.split(":", 3);

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid rule format: " + line + ". Expected format: field:expression:message");
        }

        ValidationRule rule = new ValidationRule();
        rule.field = parts[0].trim();
        rule.expression = parts[1].trim();
        rule.message = parts[2].trim();

        if (rule.message.startsWith("\"") && rule.message.endsWith("\"")) {
            rule.message = rule.message.substring(1, rule.message.length() - 1);
        }

        return rule;
    }

    private static class ValidationRule {
        String field;
        String expression;
        String message;
    }
}
