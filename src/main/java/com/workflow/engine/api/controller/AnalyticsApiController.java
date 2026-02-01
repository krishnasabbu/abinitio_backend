package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.AnalyticsApiService;
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
@Tag(name = "Analytics", description = "Operations for retrieving analytics and performance insights")
public class AnalyticsApiController {
    @Autowired
    private AnalyticsApiService analyticsApiService;

    @GetMapping("/analytics/global")
    @Operation(summary = "Get global analytics", description = "Retrieve global analytics across all workflows and executions")
    @ApiResponse(responseCode = "200", description = "Global analytics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getGlobalAnalytics() {
        Map<String, Object> analytics = analyticsApiService.getGlobalAnalytics();
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/analytics/trends")
    @Operation(summary = "Get analytics trends", description = "Retrieve analytics trends over a specified time period")
    @ApiResponse(responseCode = "200", description = "Trends retrieved successfully")
    public ResponseEntity<Map<String, Object>> getAnalyticsTrends(
            @Parameter(description = "Number of days of historical data to analyze")
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> trends = analyticsApiService.getAnalyticsTrends(days);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/analytics/node-types")
    @Operation(summary = "Get node type statistics", description = "Retrieve statistics grouped by node type")
    @ApiResponse(responseCode = "200", description = "Node type statistics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getNodeTypeStats() {
        Map<String, Object> stats = analyticsApiService.getNodeTypeStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/analytics/health/{executionId}")
    @Operation(summary = "Get execution health", description = "Retrieve health metrics for a specific execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution health retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionHealth(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> health = analyticsApiService.getExecutionHealth(executionId);
        return ResponseEntity.ok(health);
    }

    @GetMapping("/analytics/performance/{executionId}")
    @Operation(summary = "Get execution performance", description = "Retrieve detailed performance analysis for an execution")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution performance retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getExecutionPerformance(
            @Parameter(description = "The execution ID", required = true)
            @PathVariable String executionId) {
        Map<String, Object> performance = analyticsApiService.getExecutionPerformance(executionId);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/analytics/system-overview")
    @Operation(summary = "Get system overview analytics", description = "Retrieve comprehensive system overview analytics")
    @ApiResponse(responseCode = "200", description = "System overview analytics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getSystemOverviewAnalytics() {
        Map<String, Object> overview = analyticsApiService.getSystemOverviewAnalytics();
        return ResponseEntity.ok(overview);
    }
}
