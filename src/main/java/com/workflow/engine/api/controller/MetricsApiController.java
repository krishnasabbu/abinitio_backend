package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.MetricsApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetricsApiController {
    @Autowired
    private MetricsApiService metricsApiService;

    @GetMapping("/metrics/system/resources")
    public ResponseEntity<Map<String, Object>> getSystemResources() {
        Map<String, Object> resources = metricsApiService.getSystemResources();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/metrics/system/timeseries")
    public ResponseEntity<Map<String, Object>> getSystemTimeseries(@RequestParam(defaultValue = "24") int hours) {
        Map<String, Object> timeseries = metricsApiService.getSystemTimeseries(hours);
        return ResponseEntity.ok(timeseries);
    }

    @GetMapping("/metrics/executions/{executionId}/resources")
    public ResponseEntity<Map<String, Object>> getExecutionResources(@PathVariable String executionId) {
        Map<String, Object> resources = metricsApiService.getExecutionResources(executionId);
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/metrics/nodes/{executionId}/{nodeId}/resources")
    public ResponseEntity<Map<String, Object>> getNodeResources(
            @PathVariable String executionId,
            @PathVariable String nodeId) {
        Map<String, Object> resources = metricsApiService.getNodeResources(executionId, nodeId);
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/metrics/execution-modes")
    public ResponseEntity<Map<String, Object>> getExecutionModes() {
        Map<String, Object> modes = metricsApiService.getExecutionModes();
        return ResponseEntity.ok(modes);
    }

    @GetMapping("/metrics/system/overview")
    public ResponseEntity<Map<String, Object>> getSystemOverview() {
        Map<String, Object> overview = metricsApiService.getSystemOverview();
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/metrics/executions/trends")
    public ResponseEntity<Map<String, Object>> getExecutionTrends() {
        Map<String, Object> trends = metricsApiService.getExecutionTrends();
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/metrics/executions/heatmap")
    public ResponseEntity<Map<String, Object>> getExecutionHeatmap() {
        Map<String, Object> heatmap = metricsApiService.getExecutionHeatmap();
        return ResponseEntity.ok(heatmap);
    }

    @GetMapping("/metrics/insights")
    public ResponseEntity<Map<String, Object>> getSystemInsights() {
        Map<String, Object> insights = metricsApiService.getSystemInsights();
        return ResponseEntity.ok(insights);
    }
}
