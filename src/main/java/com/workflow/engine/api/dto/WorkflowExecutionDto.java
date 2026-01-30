package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowExecutionDto {
    @JsonProperty("id")
    private String id;

    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_time")
    private Long startTime;

    @JsonProperty("end_time")
    private Long endTime;

    @JsonProperty("total_nodes")
    private Integer totalNodes;

    @JsonProperty("completed_nodes")
    private Integer completedNodes;

    @JsonProperty("successful_nodes")
    private Integer successfulNodes;

    @JsonProperty("failed_nodes")
    private Integer failedNodes;

    @JsonProperty("total_records")
    private Long totalRecords;

    @JsonProperty("total_execution_time_ms")
    private Long totalExecutionTimeMs;

    @JsonProperty("error_message")
    private String errorMessage;

    public WorkflowExecutionDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public Integer getTotalNodes() { return totalNodes; }
    public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }

    public Integer getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(Integer completedNodes) { this.completedNodes = completedNodes; }

    public Integer getSuccessfulNodes() { return successfulNodes; }
    public void setSuccessfulNodes(Integer successfulNodes) { this.successfulNodes = successfulNodes; }

    public Integer getFailedNodes() { return failedNodes; }
    public void setFailedNodes(Integer failedNodes) { this.failedNodes = failedNodes; }

    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }

    public Long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
    public void setTotalExecutionTimeMs(Long totalExecutionTimeMs) { this.totalExecutionTimeMs = totalExecutionTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
