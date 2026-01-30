package com.workflow.engine.execution.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class EdgeBufferStore {

    private static final Logger logger = LoggerFactory.getLogger(EdgeBufferStore.class);

    private final Map<String, List<Map<String, Object>>> buffers = new ConcurrentHashMap<>();
    private final AtomicLong totalRecordCount = new AtomicLong(0);
    private final long maxBufferSize;

    private final Object lock = new Object();

    public EdgeBufferStore() {
        this(50_000);
    }

    public EdgeBufferStore(long maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    public void addRecord(String executionId, String targetNodeId, String targetPort, Map<String, Object> record) {
        String key = buildKey(executionId, targetNodeId, targetPort);
        synchronized (lock) {
            long newCount = totalRecordCount.incrementAndGet();
            if (newCount > maxBufferSize) {
                totalRecordCount.decrementAndGet();
                String errorMsg = String.format(
                    "Edge buffer overflow for executionId=%s edge=%s:%s limit=%d",
                    executionId, targetNodeId, targetPort, maxBufferSize
                );
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
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
        List<Map<String, Object>> removed = buffers.remove(key);
        if (removed != null) {
            totalRecordCount.addAndGet(-removed.size());
        }
    }

    public void clearExecution(String executionId) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : buffers.keySet()) {
            if (key.startsWith(executionId + ":")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            List<Map<String, Object>> removed = buffers.remove(key);
            if (removed != null) {
                totalRecordCount.addAndGet(-removed.size());
            }
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
