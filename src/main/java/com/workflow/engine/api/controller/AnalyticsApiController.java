package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.AnalyticsApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyticsApiController {
    @Autowired
    private AnalyticsApiService analyticsApiService;

    @GetMapping("/analytics/global")
    public ResponseEntity<Map<String, Object>> getGlobalAnalytics() {
        Map<String, Object> analytics = analyticsApiService.getGlobalAnalytics();
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/analytics/trends")
    public ResponseEntity<Map<String, Object>> getAnalyticsTrends(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> trends = analyticsApiService.getAnalyticsTrends(days);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/analytics/node-types")
    public ResponseEntity<Map<String, Object>> getNodeTypeStats() {
        Map<String, Object> stats = analyticsApiService.getNodeTypeStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/analytics/health/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionHealth(@PathVariable String executionId) {
        Map<String, Object> health = analyticsApiService.getExecutionHealth(executionId);
        return ResponseEntity.ok(health);
    }

    @GetMapping("/analytics/performance/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionPerformance(@PathVariable String executionId) {
        Map<String, Object> performance = analyticsApiService.getExecutionPerformance(executionId);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/analytics/system-overview")
    public ResponseEntity<Map<String, Object>> getSystemOverviewAnalytics() {
        Map<String, Object> overview = analyticsApiService.getSystemOverviewAnalytics();
        return ResponseEntity.ok(overview);
    }
}
