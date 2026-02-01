package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.MetricsApiService;
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
@Tag(name = "Metrics", description = "Operations for retrieving system and execution metrics")
public class MetricsApiController {
    @Autowired
    private MetricsApiService metricsApiService;

    @GetMapping(value = "/metrics/system/resources", produces = "application/json")
    @Operation(summary = "Get system resources", description = "Retrieve current system resource utilization metrics")
    @ApiResponse(responseCode = "200", description = "System resources retrieved successfully")
    public ResponseEntity<Map<String, Object>> getSystemResources() {
        Map<String, Object> resources = metricsApiService.getSystemResources();
        return ResponseEntity.ok(resources);
    }

    @GetMapping(value = "/metrics/system/timeseries", produces = "application/json")
    @Operation(summary = "Get system timeseries metrics", description = "Retrieve system metrics over a time period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Timeseries metrics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getSystemTimeseries(
            @Parameter(description = "Number of hours of historical data to retrieve")
            @RequestParam(defaultValue = "24") int hours) {
        Map<String, Object> timeseries = metricsApiService.getSystemTimeseries(hours);
        return ResponseEntity.ok(timeseries);
    }

    @GetMapping(value = "/metrics/executions/{executionId}/resources", produces = "application/json")
    @Operation(summary = "Get execution resources", description = "Retrieve resource utilization metrics for a specific execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution resources retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionResources(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> resources = metricsApiService.getExecutionResources(executionId);
        return ResponseEntity.ok(resources);
    }

    @GetMapping(value = "/metrics/nodes/{executionId}/{nodeId}/resources", produces = "application/json")
    @Operation(summary = "Get node resources", description = "Retrieve resource utilization metrics for a specific node")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Node resources retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution or node not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getNodeResources(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId,
            @Parameter(description = "The node ID", required = true)
            @PathVariable String nodeId) {
        Map<String, Object> resources = metricsApiService.getNodeResources(executionId, nodeId);
        return ResponseEntity.ok(resources);
    }

    @GetMapping(value = "/metrics/execution-modes", produces = "application/json")
    @Operation(summary = "Get execution modes metrics", description = "Retrieve metrics grouped by execution mode")
    @ApiResponse(responseCode = "200", description = "Execution modes metrics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getExecutionModes() {
        Map<String, Object> modes = metricsApiService.getExecutionModes();
        return ResponseEntity.ok(modes);
    }

    @GetMapping(value = "/metrics/system/overview", produces = "application/json")
    @Operation(summary = "Get system overview", description = "Retrieve a comprehensive system overview")
    @ApiResponse(responseCode = "200", description = "System overview retrieved successfully")
    public ResponseEntity<Map<String, Object>> getSystemOverview() {
        Map<String, Object> overview = metricsApiService.getSystemOverview();
        return ResponseEntity.ok(overview);
    }

    @GetMapping(value = "/metrics/executions/trends", produces = "application/json")
    @Operation(summary = "Get execution trends", description = "Retrieve execution trends over time")
    @ApiResponse(responseCode = "200", description = "Execution trends retrieved successfully")
    public ResponseEntity<Map<String, Object>> getExecutionTrends() {
        Map<String, Object> trends = metricsApiService.getExecutionTrends();
        return ResponseEntity.ok(trends);
    }

    @GetMapping(value = "/metrics/executions/heatmap", produces = "application/json")
    @Operation(summary = "Get execution heatmap", description = "Retrieve execution heatmap data for visualization")
    @ApiResponse(responseCode = "200", description = "Execution heatmap retrieved successfully")
    public ResponseEntity<Map<String, Object>> getExecutionHeatmap() {
        Map<String, Object> heatmap = metricsApiService.getExecutionHeatmap();
        return ResponseEntity.ok(heatmap);
    }

    @GetMapping(value = "/metrics/insights", produces = "application/json")
    @Operation(summary = "Get system insights", description = "Retrieve AI-powered insights about system performance")
    @ApiResponse(responseCode = "200", description = "System insights retrieved successfully")
    public ResponseEntity<Map<String, Object>> getSystemInsights() {
        Map<String, Object> insights = metricsApiService.getSystemInsights();
        return ResponseEntity.ok(insights);
    }
}
