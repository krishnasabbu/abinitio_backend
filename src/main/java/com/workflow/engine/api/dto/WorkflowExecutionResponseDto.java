package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowExecutionResponseDto {
    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("workflow_id")
    private String workflowId;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_time")
    private Long startTime;

    @JsonProperty("end_time")
    private Long endTime;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @JsonProperty("total_nodes")
    private Integer totalNodes;

    @JsonProperty("completed_nodes")
    private Integer completedNodes;

    @JsonProperty("successful_nodes")
    private Integer successfulNodes;

    @JsonProperty("failed_nodes")
    private Integer failedNodes;

    @JsonProperty("node_statuses")
    private List<NodeStatusDto> nodeStatuses;

    @JsonProperty("error_message")
    private String errorMessage;

    public WorkflowExecutionResponseDto() {}

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Integer getTotalNodes() { return totalNodes; }
    public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }

    public Integer getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(Integer completedNodes) { this.completedNodes = completedNodes; }

    public Integer getSuccessfulNodes() { return successfulNodes; }
    public void setSuccessfulNodes(Integer successfulNodes) { this.successfulNodes = successfulNodes; }

    public Integer getFailedNodes() { return failedNodes; }
    public void setFailedNodes(Integer failedNodes) { this.failedNodes = failedNodes; }

    public List<NodeStatusDto> getNodeStatuses() { return nodeStatuses; }
    public void setNodeStatuses(List<NodeStatusDto> nodeStatuses) { this.nodeStatuses = nodeStatuses; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
