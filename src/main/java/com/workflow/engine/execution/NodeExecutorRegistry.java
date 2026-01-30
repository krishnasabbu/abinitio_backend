package com.workflow.engine.execution;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor<?, ?>> executors = new HashMap<>();

    public void register(NodeExecutor<?, ?> executor) {
        executors.put(executor.getNodeType(), executor);
    }

    public NodeExecutor<?, ?> getExecutor(String nodeType) {
        NodeExecutor<?, ?> executor = executors.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for node type: " + nodeType);
        }
        return executor;
    }

    public boolean hasExecutor(String nodeType) {
        return executors.containsKey(nodeType);
    }

    public void unregister(String nodeType) {
        executors.remove(nodeType);
    }
}
