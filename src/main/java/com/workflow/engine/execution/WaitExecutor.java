package com.workflow.engine.execution;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WaitExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "Wait";
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> buildProcessor(
            NodeExecutionContext context) {
        return item -> {
            Map<String, Object> config = context.getNodeConfig();
            String waitType = (String) config.getOrDefault("waitType", "TIME");

            if ("TIME".equals(waitType)) {
                long durationSeconds = Long.parseLong(
                    config.getOrDefault("durationSeconds", "300").toString());
                try {
                    Thread.sleep(durationSeconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return item;
        };
    }
}
