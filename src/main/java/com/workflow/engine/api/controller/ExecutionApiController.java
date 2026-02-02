package com.workflow.engine.api.controller;

import com.workflow.engine.api.dto.WorkflowExecutionDto;
import com.workflow.engine.api.dto.NodeExecutionDto;
import com.workflow.engine.api.dto.WorkflowExecutionResponseDto;
import com.workflow.engine.api.service.ExecutionApiService;
import com.workflow.engine.controller.WorkflowController;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Executions", description = "Operations related to workflow execution and monitoring")
public class ExecutionApiController {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionApiController.class);
    @Autowired
    private ExecutionApiService executionApiService;
    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    @PostMapping(value = "/execute", produces = "application/json")
    @Operation(summary = "Execute workflow", description = "Execute a workflow with specified execution mode and configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Workflow execution accepted"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Workflow not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<WorkflowExecutionResponseDto> executeWorkflow(@RequestBody WorkflowDefinition workflow) {
        logger.info("Received workflow execution request: {}", workflow.getName());
        try {
            workflow.setId(UUID.randomUUID().toString());
            WorkflowExecutionResponseDto response = workflowExecutionService.executeWorkflowWithResponse(workflow);
            logger.info("Workflow execution completed: {}", workflow.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Workflow execution failed: {}", workflow.getName(), e);
            WorkflowExecutionResponseDto errorResponse = new WorkflowExecutionResponseDto();
            errorResponse.setWorkflowName(workflow.getName());
            errorResponse.setStatus("FAILED");
            errorResponse.setErrorMessage("Workflow execution failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping(value = "/executions", produces = "application/json")
    @Operation(summary = "Get execution history", description = "Retrieve execution history, optionally filtered by workflow ID")
    @ApiResponse(responseCode = "200", description = "Execution history retrieved successfully")
    public ResponseEntity<List<WorkflowExecutionDto>> getExecutionHistory(
            @Parameter(description = "Filter by workflow ID")
            @RequestParam(required = false) String workflow_id,
            @Parameter(description = "Limit number of results")
            @RequestParam(required = false, defaultValue = "50") int limit) {
        List<WorkflowExecutionDto> executions = executionApiService.getExecutionHistory(workflow_id, limit);
        return ResponseEntity.ok(executions);
    }

    @GetMapping(value = "/execution/{executionId}", produces = "application/json")
    @Operation(summary = "Get execution by ID", description = "Retrieve detailed information about a specific execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution found"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<WorkflowExecutionDto> getExecutionById(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        WorkflowExecutionDto execution = executionApiService.getExecutionById(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    @GetMapping(value = "/executions/{executionId}/nodes", produces = "application/json")
    @Operation(summary = "Get node executions", description = "Retrieve execution details for all nodes in a workflow execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Node executions retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<NodeExecutionDto>> getNodeExecutions(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        List<NodeExecutionDto> nodes = executionApiService.getNodeExecutions(executionId);
        return ResponseEntity.ok(nodes);
    }

    @GetMapping(value = "/executions/{executionId}/timeline", produces = "application/json")
    @Operation(summary = "Get execution timeline", description = "Retrieve the execution timeline showing the sequence of node executions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Timeline retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionTimeline(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> timeline = executionApiService.getExecutionTimeline(executionId);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping(value = "/executions/{executionId}/metrics", produces = "application/json")
    @Operation(summary = "Get execution metrics", description = "Retrieve performance metrics for a workflow execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionMetrics(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> metrics = executionApiService.getExecutionMetrics(executionId);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping(value = "/executions/{executionId}/bottlenecks", produces = "application/json")
    @Operation(summary = "Get execution bottlenecks", description = "Identify performance bottlenecks in a workflow execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bottlenecks identified successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionBottlenecks(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId,
            @Parameter(description = "Number of top bottlenecks to return")
            @RequestParam(defaultValue = "5") int top_n) {
        Map<String, Object> bottlenecks = executionApiService.getExecutionBottlenecks(executionId, top_n);
        return ResponseEntity.ok(bottlenecks);
    }

    @PostMapping(value = "/executions/{executionId}/rerun", produces = "application/json")
    @Operation(summary = "Rerun execution", description = "Rerun a workflow execution, optionally from a specific node")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Execution rerun accepted"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> rerunExecution(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId,
            @Parameter(description = "Node ID to restart from (for partial reruns)")
            @RequestParam(required = false) String from_node_id) {
        Map<String, Object> result = executionApiService.rerunExecution(executionId, from_node_id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping(value = "/executions/{executionId}/rerun-from-failed", produces = "application/json")
    @Operation(summary = "Rerun from failed node", description = "Rerun a workflow execution from the first failed node")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Execution rerun accepted"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> rerunFromFailed(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> result = executionApiService.rerunFromFailed(executionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping(value = "/executions/{executionId}/cancel", produces = "application/json")
    @Operation(summary = "Cancel execution", description = "Cancel an ongoing workflow execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution cancelled successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> cancelExecution(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> result = executionApiService.cancelExecution(executionId);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/executions/recent", produces = "application/json")
    @Operation(summary = "Get recent executions", description = "Retrieve the most recent workflow executions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recent executions retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getRecentExecutions(
            @Parameter(description = "Number of recent executions to retrieve", required = true)
            @RequestParam int limit) {
        Map<String, Object> recent = executionApiService.getRecentExecutions(limit);
        return ResponseEntity.ok(recent);
    }
}
