package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class WorkflowDto {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("workflow_data")
    private Map<String, Object> workflowData;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    public WorkflowDto() {}

    public WorkflowDto(String id, String name, String description, Map<String, Object> workflowData, String createdAt, String updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.workflowData = workflowData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getWorkflowData() { return workflowData; }
    public void setWorkflowData(Map<String, Object> workflowData) { this.workflowData = workflowData; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
