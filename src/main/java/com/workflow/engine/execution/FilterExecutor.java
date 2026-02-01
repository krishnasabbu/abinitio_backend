package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingItemWriter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for filtering items based on SpEL expressions.
 *
 * Evaluates a SpEL (Spring Expression Language) condition for each input item.
 * Items for which the condition evaluates to true are passed to the output;
 * items evaluating to false are filtered out (dropped).
 *
 * Configuration:
 * - condition: (required) SpEL expression that evaluates to boolean
 *              The item itself is available as the root object in the expression
 *              Example: "name.startsWith('A')" or "age > 18"
 *
 * Output:
 * Sets "outputItems" variable with the filtered list of items.
 *
 * Thread safety: Thread-safe. SpEL parser is stateless.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class FilterExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(FilterExecutor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "Filter";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("Creating filter reader");

        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingContext = (RoutingNodeExecutionContext) context;
            String executionId = routingContext.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();

            logger.debug("Using BufferedItemReader for node {}", nodeId);
            return new BufferedItemReader(executionId, nodeId, "in", routingContext.getRoutingContext().getBufferStore());
        } else {
            List<Map<String, Object>> items = new ArrayList<>();
            List<Map<String, Object>> contextItems = (List<Map<String, Object>>) context.getVariable("outputItems");
            if (contextItems != null) {
                items.addAll(contextItems);
                logger.debug("Filter reader found {} items from outputItems variable", items.size());
            } else {
                logger.debug("No outputItems variable found, using empty list");
            }
            return new ListItemReader<>(items);
        }
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        logger.debug("Creating filter processor");
        JsonNode config = context.getNodeDefinition().getConfig();
        String condition = config.get("condition").asText();
        logger.debug("Filter condition: {}", condition);

        boolean isRouting = context instanceof RoutingNodeExecutionContext;

        return item -> {
            try {
                StandardEvaluationContext evalContext = new StandardEvaluationContext(item);
                Object result = parser.parseExpression(condition).getValue(evalContext);

                boolean passed = result instanceof Boolean && (Boolean) result;

                if (isRouting) {
                    Map<String, Object> itemWithRoute = new HashMap<>(item);
                    itemWithRoute.put("_routePort", passed ? "out" : "reject");
                    logger.debug("Item routed to: {}", itemWithRoute.get("_routePort"));
                    return itemWithRoute;
                } else {
                    if (passed) {
                        logger.debug("Item passed filter condition");
                        return item;
                    } else {
                        logger.debug("Item filtered out (condition evaluated to false)");
                        return null;
                    }
                }
            } catch (Exception e) {
                logger.warn("Filter evaluation failed for item: {}", e.getMessage());
                return isRouting ? item : null;
            }
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        logger.debug("Creating filter writer");

        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingContext = (RoutingNodeExecutionContext) context;
            RoutingItemWriter routingWriter = new RoutingItemWriter(routingContext.getRoutingContext(), "_routePort");
            return items -> {
                logger.debug("Filter routing {} items", items.size());
                routingWriter.write(items);
            };
        } else {
            return items -> {
                List<Map<String, Object>> outputList = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    if (item != null) {
                        outputList.add(item);
                    }
                }
                logger.info("Filter output: {} items passed filter", outputList.size());
                context.setVariable("outputItems", outputList);
            };
        }
    }

    @Override
    public void validate(NodeExecutionContext context) {
        logger.debug("Validating Filter configuration");
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("condition")) {
            logger.error("Filter configuration invalid - missing 'condition' property");
            throw new IllegalArgumentException("Filter node requires 'condition' in config");
        }

        String condition = config.get("condition").asText();
        if (condition == null || condition.trim().isEmpty()) {
            logger.error("Filter configuration invalid - 'condition' cannot be empty");
            throw new IllegalArgumentException("Filter 'condition' cannot be empty");
        }
        logger.debug("Filter configuration valid");
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
