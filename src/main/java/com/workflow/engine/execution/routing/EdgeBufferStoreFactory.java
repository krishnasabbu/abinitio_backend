package com.workflow.engine.execution.routing;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EdgeBufferStoreFactory {

    private final Map<String, EdgeBufferStore> stores = new HashMap<>();

    public EdgeBufferStore getOrCreate(String executionId) {
        return stores.computeIfAbsent(executionId, k -> new EdgeBufferStore());
    }

    public EdgeBufferStore get(String executionId) {
        return stores.get(executionId);
    }

    public void cleanup(String executionId) {
        EdgeBufferStore store = stores.remove(executionId);
        if (store != null) {
            store.clearExecution(executionId);
        }
    }
}
