package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.workflow.engine.api.util.TimestampConverter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeExecutionDto {
    @JsonProperty("id")
    private String id;

    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("node_label")
    private String nodeLabel;

    @JsonProperty("node_type")
    private String nodeType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("end_time")
    private String endTime;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @JsonProperty("records_processed")
    private Long recordsProcessed;

    @JsonProperty("input_records")
    private Long inputRecords;

    @JsonProperty("output_records")
    private Long outputRecords;

    @JsonProperty("input_bytes")
    private Long inputBytes;

    @JsonProperty("output_bytes")
    private Long outputBytes;

    @JsonProperty("records_per_second")
    private Double recordsPerSecond;

    @JsonProperty("bytes_per_second")
    private Double bytesPerSecond;

    @JsonProperty("queue_wait_time_ms")
    private Long queueWaitTimeMs;

    @JsonProperty("depth_in_dag")
    private Integer depthInDag;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("output_summary")
    private Object outputSummary;

    @JsonProperty("logs")
    private Object logs;

    public NodeExecutionDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeLabel() { return nodeLabel; }
    public void setNodeLabel(String nodeLabel) { this.nodeLabel = nodeLabel; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public void setStartTimeMs(Long startTimeMs) {
        this.startTime = TimestampConverter.toISO8601(startTimeMs);
    }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public void setEndTimeMs(Long endTimeMs) {
        this.endTime = TimestampConverter.toISO8601(endTimeMs);
    }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Long getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(Long recordsProcessed) { this.recordsProcessed = recordsProcessed; }

    public Long getInputRecords() { return inputRecords; }
    public void setInputRecords(Long inputRecords) { this.inputRecords = inputRecords; }

    public Long getOutputRecords() { return outputRecords; }
    public void setOutputRecords(Long outputRecords) { this.outputRecords = outputRecords; }

    public Long getInputBytes() { return inputBytes; }
    public void setInputBytes(Long inputBytes) { this.inputBytes = inputBytes; }

    public Long getOutputBytes() { return outputBytes; }
    public void setOutputBytes(Long outputBytes) { this.outputBytes = outputBytes; }

    public Double getRecordsPerSecond() { return recordsPerSecond; }
    public void setRecordsPerSecond(Double recordsPerSecond) { this.recordsPerSecond = recordsPerSecond; }

    public Double getBytesPerSecond() { return bytesPerSecond; }
    public void setBytesPerSecond(Double bytesPerSecond) { this.bytesPerSecond = bytesPerSecond; }

    public Long getQueueWaitTimeMs() { return queueWaitTimeMs; }
    public void setQueueWaitTimeMs(Long queueWaitTimeMs) { this.queueWaitTimeMs = queueWaitTimeMs; }

    public Integer getDepthInDag() { return depthInDag; }
    public void setDepthInDag(Integer depthInDag) { this.depthInDag = depthInDag; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Object getOutputSummary() { return outputSummary; }
    public void setOutputSummary(Object outputSummary) { this.outputSummary = outputSummary; }

    public Object getLogs() { return logs; }
    public void setLogs(Object logs) { this.logs = logs; }
}
