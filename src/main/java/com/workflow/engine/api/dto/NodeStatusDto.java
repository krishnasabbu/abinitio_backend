package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeStatusDto {
    @JsonProperty("node_name")
    private String nodeName;

    @JsonProperty("node_type")
    private String nodeType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_time")
    private Long startTime;

    @JsonProperty("end_time")
    private Long endTime;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @JsonProperty("records_read")
    private Long recordsRead;

    @JsonProperty("records_written")
    private Long recordsWritten;

    @JsonProperty("skip_count")
    private Long skipCount;

    @JsonProperty("error_message")
    private String errorMessage;

    public NodeStatusDto() {}

    public NodeStatusDto(String nodeName, String nodeType, String status) {
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.status = status;
    }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Long getRecordsRead() { return recordsRead; }
    public void setRecordsRead(Long recordsRead) { this.recordsRead = recordsRead; }

    public Long getRecordsWritten() { return recordsWritten; }
    public void setRecordsWritten(Long recordsWritten) { this.recordsWritten = recordsWritten; }

    public Long getSkipCount() { return skipCount; }
    public void setSkipCount(Long skipCount) { this.skipCount = skipCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
