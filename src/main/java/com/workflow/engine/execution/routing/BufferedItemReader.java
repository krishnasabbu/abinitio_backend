package com.workflow.engine.execution.routing;

import org.springframework.batch.item.ItemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class BufferedItemReader implements ItemReader<Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(BufferedItemReader.class);

    private final String executionId;
    private final String targetNodeId;
    private final String targetPort;
    private final EdgeBufferStore bufferStore;
    private int currentIndex = 0;
    private List<Map<String, Object>> cachedRecords;

    public BufferedItemReader(String executionId, String targetNodeId, String targetPort, EdgeBufferStore bufferStore) {
        this.executionId = executionId;
        this.targetNodeId = targetNodeId;
        this.targetPort = targetPort != null ? targetPort : "in";
        this.bufferStore = bufferStore;
    }

    @Override
    public Map<String, Object> read() {
        if (cachedRecords == null) {
            cachedRecords = bufferStore.getRecords(executionId, targetNodeId, targetPort);
            logger.debug("BufferedItemReader fetched {} records for {}", cachedRecords.size(), targetNodeId);
        }

        if (cachedRecords.isEmpty()) {
            return null;
        }

        if (currentIndex < cachedRecords.size()) {
            return cachedRecords.get(currentIndex++);
        }

        return null;
    }

    public void resetForNextBatch() {
        this.cachedRecords = bufferStore.getRecords(executionId, targetNodeId, targetPort);
        this.currentIndex = 0;
    }
}
