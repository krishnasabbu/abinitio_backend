package com.workflow.engine.execution.routing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EdgeBufferStore {

    private final Map<String, List<Map<String, Object>>> buffers = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    public void addRecord(String executionId, String targetNodeId, String targetPort, Map<String, Object> record) {
        String key = buildKey(executionId, targetNodeId, targetPort);
        synchronized (lock) {
            buffers.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                   .add(record);
        }
    }

    public List<Map<String, Object>> getRecords(String executionId, String targetNodeId, String targetPort) {
        String key = buildKey(executionId, targetNodeId, targetPort);
        return buffers.getOrDefault(key, new ArrayList<>());
    }

    public void clearBuffer(String executionId, String targetNodeId, String targetPort) {
        String key = buildKey(executionId, targetNodeId, targetPort);
        buffers.remove(key);
    }

    public void clearExecution(String executionId) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : buffers.keySet()) {
            if (key.startsWith(executionId + ":")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            buffers.remove(key);
        }
    }

    public boolean hasRecords(String executionId, String targetNodeId, String targetPort) {
        String key = buildKey(executionId, targetNodeId, targetPort);
        List<Map<String, Object>> list = buffers.get(key);
        return list != null && !list.isEmpty();
    }

    private String buildKey(String executionId, String targetNodeId, String targetPort) {
        return executionId + ":" + targetNodeId + ":" + (targetPort != null ? targetPort : "default");
    }
}
