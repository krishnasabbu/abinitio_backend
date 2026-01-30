package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEntryDto {
    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("datetime")
    private String datetime;

    @JsonProperty("level")
    private String level;

    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("workflow_id")
    private String workflowId;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @JsonProperty("stack_trace")
    private String stackTrace;

    public LogEntryDto() {}

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public String getDatetime() { return datetime; }
    public void setDatetime(String datetime) { this.datetime = datetime; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
}
