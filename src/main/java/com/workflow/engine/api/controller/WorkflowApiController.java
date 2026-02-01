package com.workflow.engine.api.controller;

import com.workflow.engine.api.dto.WorkflowDto;
import com.workflow.engine.api.service.WorkflowApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Workflows", description = "Operations related to workflow definitions and management")
public class WorkflowApiController {
    @Autowired
    private WorkflowApiService workflowApiService;

    @GetMapping("/workflows")
    @Operation(summary = "List all workflows", description = "Retrieve a list of all workflow definitions stored in the system")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved workflows")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<List<WorkflowDto>> getAllWorkflows() {
        List<WorkflowDto> workflows = workflowApiService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/workflows/{id}")
    @Operation(summary = "Get workflow by ID", description = "Retrieve a specific workflow definition by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workflow found"),
        @ApiResponse(responseCode = "404", description = "Workflow not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<WorkflowDto> getWorkflowById(
            @Parameter(description = "The workflow ID", required = true)
            @PathVariable String id) {
        WorkflowDto workflow = workflowApiService.getWorkflowById(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(workflow);
    }

    @PostMapping("/workflows")
    @Operation(summary = "Create a new workflow", description = "Create a new workflow definition with the provided configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Workflow created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody WorkflowDto request) {
        String id = workflowApiService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/workflows/{id}")
    @Operation(summary = "Update workflow", description = "Update an existing workflow definition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workflow updated successfully"),
        @ApiResponse(responseCode = "404", description = "Workflow not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> updateWorkflow(
            @Parameter(description = "The workflow ID", required = true)
            @PathVariable String id,
            @RequestBody WorkflowDto request) {
        workflowApiService.updateWorkflow(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/workflows/{id}")
    @Operation(summary = "Delete workflow", description = "Delete a workflow definition by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Workflow deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Workflow not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteWorkflow(
            @Parameter(description = "The workflow ID", required = true)
            @PathVariable String id) {
        workflowApiService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workflows/stats")
    @Operation(summary = "Get workflow statistics", description = "Retrieve aggregated statistics across all workflows")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<Map<String, Object>> getWorkflowStats() {
        Map<String, Object> stats = workflowApiService.getWorkflowStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/workflows/recent")
    @Operation(summary = "Get recent workflows", description = "Retrieve the most recently created or modified workflows")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recent workflows retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getRecentWorkflows(
            @Parameter(description = "Number of recent workflows to retrieve", required = true)
            @RequestParam int limit) {
        Map<String, Object> recent = workflowApiService.getRecentWorkflows(limit);
        return ResponseEntity.ok(recent);
    }

    @GetMapping("/workflows/{workflowId}/analytics")
    @Operation(summary = "Get workflow analytics", description = "Retrieve detailed analytics for a specific workflow")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Workflow not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getWorkflowAnalytics(
            @Parameter(description = "The workflow ID", required = true)
            @PathVariable String workflowId) {
        Map<String, Object> analytics = workflowApiService.getWorkflowAnalytics(workflowId);
        return ResponseEntity.ok(analytics);
    }
}
