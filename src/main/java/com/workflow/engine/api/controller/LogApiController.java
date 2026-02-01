package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.LogApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Logs", description = "Operations for retrieving and searching execution logs")
public class LogApiController {
    @Autowired
    private LogApiService logApiService;

    @GetMapping(value = "/logs/executions", produces = "application/json")
    @Operation(summary = "List execution logs", description = "Retrieve a list of all execution logs")
    @ApiResponse(responseCode = "200", description = "Execution logs retrieved successfully")
    public ResponseEntity<Map<String, Object>> listExecutionLogs() {
        Map<String, Object> logs = logApiService.listExecutionLogs();
        return ResponseEntity.ok(logs);
    }

    @GetMapping(value = "/logs/executions/{executionId}", produces = "application/json")
    @Operation(summary = "Get execution logs", description = "Retrieve detailed logs for a specific execution with filtering options")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution logs retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionLogs(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId,
            @Parameter(description = "Filter by log level (INFO, WARN, ERROR, DEBUG)")
            @RequestParam(required = false) String level,
            @Parameter(description = "Filter by node ID")
            @RequestParam(required = false) String node_id,
            @Parameter(description = "Search term to filter logs")
            @RequestParam(required = false) String search,
            @Parameter(description = "Start time in milliseconds since epoch")
            @RequestParam(required = false) Long start_time,
            @Parameter(description = "End time in milliseconds since epoch")
            @RequestParam(required = false) Long end_time,
            @Parameter(description = "Maximum number of logs to return")
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Offset for pagination")
            @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> logs = logApiService.getExecutionLogs(executionId, level, node_id, search, start_time, end_time, limit, offset);
        return ResponseEntity.ok(logs);
    }

    @GetMapping(value = "/logs/nodes/{executionId}/{nodeId}", produces = "application/json")
    @Operation(summary = "Get node logs", description = "Retrieve logs for a specific node in an execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Node logs retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution or node not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getNodeLogs(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId,
            @Parameter(description = "The node ID", required = true)
            @PathVariable String nodeId,
            @Parameter(description = "Filter by log level (INFO, WARN, ERROR, DEBUG)")
            @RequestParam(required = false) String level,
            @Parameter(description = "Maximum number of logs to return")
            @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> logs = logApiService.getNodeLogs(executionId, nodeId, level, limit);
        return ResponseEntity.ok(logs);
    }

    @GetMapping(value = "/logs/search", produces = "application/json")
    @Operation(summary = "Search logs", description = "Search logs across executions with various filters")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> searchLogs(
            @Parameter(description = "Search term", required = true)
            @RequestParam String search,
            @Parameter(description = "Filter by log level (INFO, WARN, ERROR, DEBUG)")
            @RequestParam(required = false) String level,
            @Parameter(description = "Filter by execution ID")
            @RequestParam(required = false) String execution_id,
            @Parameter(description = "Start time in milliseconds since epoch")
            @RequestParam(required = false) Long start_time,
            @Parameter(description = "End time in milliseconds since epoch")
            @RequestParam(required = false) Long end_time,
            @Parameter(description = "Maximum number of results to return")
            @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> results = logApiService.searchLogs(search, level, execution_id, start_time, end_time, limit);
        return ResponseEntity.ok(results);
    }

    @GetMapping(value = "/logs/summary/{executionId}", produces = "application/json")
    @Operation(summary = "Get log summary", description = "Retrieve a summary of logs for an execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Log summary retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getLogSummary(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> summary = logApiService.getLogSummary(executionId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping(value = "/analytics/execution-logs/{executionId}", produces = "application/json")
    @Operation(summary = "Get analytics execution logs", description = "Retrieve execution logs formatted for analytics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analytics logs retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getAnalyticsExecutionLogs(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId,
            @Parameter(description = "Maximum number of logs to return")
            @RequestParam(defaultValue = "500") int limit) {
        Map<String, Object> logs = logApiService.getAnalyticsExecutionLogs(executionId, limit);
        return ResponseEntity.ok(logs);
    }

    @GetMapping(value = "/analytics/logs/{executionId}", produces = "application/json")
    @Operation(summary = "Get log analysis", description = "Retrieve analyzed logs with insights for an execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Log analysis retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getLogAnalysis(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> analysis = logApiService.getLogAnalysis(executionId);
        return ResponseEntity.ok(analysis);
    }
}
