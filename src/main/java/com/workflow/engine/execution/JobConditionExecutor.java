package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class JobConditionExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    @Override
    public String getNodeType() {
        return "JobCondition";
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> buildProcessor(
            NodeExecutionContext context) {
        return item -> {
            Map<String, Object> config = context.getNodeConfig();
            String expression = (String) config.get("expression");

            if (expression == null || expression.isEmpty()) {
                return item;
            }

            try {
                Expression expr = PARSER.parseExpression(expression);
                EvaluationContext evalContext = new StandardEvaluationContext(item);
                Boolean result = expr.getValue(evalContext, Boolean.class);

                Map<String, Object> output = new HashMap<>(item);
                output.put("__condition_result__", result != null && result);
                return output;
            } catch (Exception e) {
                throw new RuntimeException("JobCondition evaluation failed: " + e.getMessage(), e);
            }
        };
    }
}
