package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GatherExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Gather";
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> buildProcessor(
            NodeExecutionContext context) {
        return item -> {
            return item;
        };
    }
}
