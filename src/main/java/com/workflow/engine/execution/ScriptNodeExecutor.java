package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ScriptNodeExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "ScriptNode";
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(
            NodeExecutionContext context) {
        return item -> item;
    }
}
