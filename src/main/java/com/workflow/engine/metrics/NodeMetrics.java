package com.workflow.engine.metrics;

import lombok.Data;

@Data
public class NodeMetrics {
    private String nodeId;
    private String nodeType;
    private long executionTimeMs;
    private long readCount;
    private long writeCount;
    private long errorCount;
    private long skipCount;
    private String status;
    private long startTime;
    private long endTime;
}
