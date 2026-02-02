package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowExecutionDto {
    @JsonProperty("id")
    private String id;

    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("start_time")
    private Long startTimeMs;

    @JsonProperty("end_time")
    private Long endTimeMs;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @JsonProperty("total_nodes")
    private Integer totalNodes;

    @JsonProperty("successful_nodes")
    private Integer successfulNodes;

    @JsonProperty("completed_nodes")
    private Integer completedNodes;

    @JsonProperty("failed_nodes")
    private Integer failedNodes;

    @JsonProperty("total_records_processed")
    private Long totalRecordsProcessed;

    @JsonProperty("total_execution_time_ms")
    private Long totalExecutionTimeMs;

    @JsonProperty("execution_mode")
    private String executionMode;

    @JsonProperty("planning_start_time")
    private String planningStartTime;

    @JsonProperty("max_parallel_nodes")
    private Integer maxParallelNodes;

    @JsonProperty("peak_workers")
    private Integer peakWorkers;

    @JsonProperty("total_input_records")
    private Long totalInputRecords;

    @JsonProperty("total_output_records")
    private Long totalOutputRecords;

    @JsonProperty("total_bytes_read")
    private Long totalBytesRead;

    @JsonProperty("total_bytes_written")
    private Long totalBytesWritten;

    @JsonProperty("error")
    private String error;

    public WorkflowExecutionDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }

    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }

    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Integer getTotalNodes() { return totalNodes; }
    public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }

    public Integer getSuccessfulNodes() { return successfulNodes; }
    public void setSuccessfulNodes(Integer successfulNodes) { this.successfulNodes = successfulNodes; }

    public Integer getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(Integer completedNodes) { this.completedNodes = completedNodes; }

    public Integer getFailedNodes() { return failedNodes; }
    public void setFailedNodes(Integer failedNodes) { this.failedNodes = failedNodes; }

    public Long getTotalRecordsProcessed() { return totalRecordsProcessed; }
    public void setTotalRecordsProcessed(Long totalRecordsProcessed) { this.totalRecordsProcessed = totalRecordsProcessed; }

    public Long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
    public void setTotalExecutionTimeMs(Long totalExecutionTimeMs) { this.totalExecutionTimeMs = totalExecutionTimeMs; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getPlanningStartTime() { return planningStartTime; }
    public void setPlanningStartTime(String planningStartTime) { this.planningStartTime = planningStartTime; }

    public Integer getMaxParallelNodes() { return maxParallelNodes; }
    public void setMaxParallelNodes(Integer maxParallelNodes) { this.maxParallelNodes = maxParallelNodes; }

    public Integer getPeakWorkers() { return peakWorkers; }
    public void setPeakWorkers(Integer peakWorkers) { this.peakWorkers = peakWorkers; }

    public Long getTotalInputRecords() { return totalInputRecords; }
    public void setTotalInputRecords(Long totalInputRecords) { this.totalInputRecords = totalInputRecords; }

    public Long getTotalOutputRecords() { return totalOutputRecords; }
    public void setTotalOutputRecords(Long totalOutputRecords) { this.totalOutputRecords = totalOutputRecords; }

    public Long getTotalBytesRead() { return totalBytesRead; }
    public void setTotalBytesRead(Long totalBytesRead) { this.totalBytesRead = totalBytesRead; }

    public Long getTotalBytesWritten() { return totalBytesWritten; }
    public void setTotalBytesWritten(Long totalBytesWritten) { this.totalBytesWritten = totalBytesWritten; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
