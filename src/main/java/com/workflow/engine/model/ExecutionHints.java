package com.workflow.engine.model;

import lombok.Data;

@Data
public class ExecutionHints {
    private ExecutionMode mode = ExecutionMode.SERIAL;
    private Integer chunkSize;
    private Integer partitionCount;
    private Integer maxRetries;
    private Long timeout;
}
